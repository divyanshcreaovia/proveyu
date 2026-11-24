package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * College exams (sessionals) on the college's own tests: one attempt in a time window, branch targeting, question pool and shuffled
 * options, written answers marked by a teacher, hidden results until released, live monitor with extra time, and the marks CSV.
 * Practice tests keep working as before (retakes, answers shown).
 */
class ExamsIT extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    /** Section 1: pool of 3 MCQs (pick 2, 2 marks each); section 2: one written question (5 marks). */
    private static final String EXAM_JSON = """
            {"title": "Sessional 1", "durationMinutes": 30, "sections": [
              {"name": "Basics", "pick": 2, "questions": [
                {"type": "MCQ", "text": "2 + 2 = ?", "options": ["3", "4", "5", "6"], "answer": 1, "marks": 2, "explanation": "four"},
                {"type": "MCQ", "text": "3 x 3 = ?", "options": ["6", "9", "12", "8"], "answer": 1, "marks": 2},
                {"type": "MCQ", "text": "10 - 4 = ?", "options": ["6", "4", "5", "7"], "answer": 0, "marks": 2}]},
              {"name": "Theory", "questions": [
                {"type": "TEXT", "title": "OOP", "text": "Explain encapsulation.", "marks": 5, "explanation": "Hiding data behind methods"}]}]}""";

    private static final Map<String, String> CORRECT = Map.of("2 + 2 = ?", "4", "3 x 3 = ?", "9", "10 - 4 = ?", "6");

    @Test
    void sessionalExamEndToEnd() throws Exception {
        String tpo = registerCollege("exm");
        String cse = addBranch(tpo, "CSE");
        String ece = addBranch(tpo, "ECE");
        JsonNode sCse = addStudent(tpo, cse, "E" + uid(), 8.0, 0);
        String student = activate(sCse.get("email").asText());
        String other = activate(addStudent(tpo, ece, "F" + uid(), 8.0, 0).get("email").asText());
        String otherTpo = registerCollege("exo");

        String testId = upload(tpo, EXAM_JSON);
        JsonNode summary = get("/api/v1/college/prep/tests", tpo).data().get(0);
        assertThat(summary.get("questionCount").asInt()).isEqualTo(3);           // 2 picked + 1 written
        assertThat(summary.get("totalMarks").asInt()).isEqualTo(9);              // 2 x 2 + 5
        assertThat(summary.path("exam").get("mode").asText()).isEqualTo("PRACTICE");

        // settings: validation, then an exam for CSE only, live now, shuffled options, results hidden
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", null, null, List.of(), false, false)).body()
                .path("errorCode").asText()).isEqualTo("EXAM_WINDOW_REQUIRED");
        assertThat(put("/api/v1/college/prep/tests/" + testId + "/settings", otherTpo, settings("EXAM", now, now.plusSeconds(3600), List.of(), false, false))
                .status()).isEqualTo(404);
        Resp saved = put("/api/v1/college/prep/tests/" + testId + "/settings", tpo,
                settings("EXAM", now.minusSeconds(60), now.plusSeconds(600), List.of(cse), true, false));
        assertThat(saved.status()).as(saved.body().toString()).isEqualTo(200);
        assertThat(saved.data().path("exam").get("branchIds").get(0).asText()).isEqualTo(cse);
        patch("/api/v1/college/prep/tests/" + testId + "/publish", tpo, Map.of("published", true));

        // targeting: CSE sees a live exam; ECE neither sees nor can start it
        JsonNode card = card(student, testId);
        assertThat(card.get("mode").asText()).isEqualTo("EXAM");
        assertThat(card.get("examStatus").asText()).isEqualTo("LIVE");
        assertThat(card.get("collegeTest").asBoolean()).isTrue();
        assertThat(card(other, testId)).isNull();
        assertThat(post("/api/v1/college/student/prep/tests/" + testId + "/start", other, null).status()).isEqualTo(404);

        // start: 2 of 3 pool questions + the written one; deadline capped at the window end; options shuffled per attempt
        JsonNode view = post("/api/v1/college/student/prep/tests/" + testId + "/start", student, null).data();
        String attemptId = view.get("attemptId").asText();
        assertThat(view.get("mode").asText()).isEqualTo("EXAM");
        assertThat(view.get("questions")).hasSize(3);
        assertThat(Instant.parse(view.get("deadlineAt").asText())).isBeforeOrEqualTo(now.plusSeconds(600));
        assertThat(jdbc.queryForObject("select max_score from prep_attempts where id = ?", Integer.class, attemptId)).isEqualTo(9);

        // answer every MCQ correctly by the text shown (shuffled index), and write the descriptive answer
        Map<String, Object> answers = new HashMap<>();
        String textQ = null;
        for (JsonNode q : view.get("questions")) {
            if (q.get("type").asText().equals("MCQ")) {
                List<String> opts = json.convertValue(q.get("options"), json.getTypeFactory().constructCollectionType(List.class, String.class));
                answers.put(q.get("id").asText(), Map.of("option", opts.indexOf(CORRECT.get(q.get("text").asText()))));
            } else {
                textQ = q.get("id").asText();
                answers.put(textQ, Map.of("text", "Encapsulation keeps the fields private and exposes methods."));
            }
        }
        assertThat(textQ).isNotNull();
        String base = "/api/v1/college/student/prep/attempts/" + attemptId;
        assertThat(put(base + "/answers", student, answers).status()).isEqualTo(200);

        // live monitor: CSE student writing, extra time moves the deadline
        JsonNode mon = get("/api/v1/college/prep/tests/" + testId + "/monitor", tpo).data();
        assertThat(mon.get("eligible").asInt()).isEqualTo(1);
        assertThat(mon.get("inProgress").asInt()).isEqualTo(1);
        assertThat(mon.get("rows").get(0).get("answered").asInt()).isEqualTo(3);
        Instant before = Instant.parse(mon.get("rows").get(0).get("deadlineAt").asText());
        JsonNode ext = post("/api/v1/college/prep/tests/" + testId + "/attempts/" + attemptId + "/extend", tpo, Map.of("minutes", 10)).data();
        assertThat(Instant.parse(ext.get("deadlineAt").asText())).isEqualTo(before.plusSeconds(600));
        assertThat(ext.get("extraMinutes").asInt()).isEqualTo(10);
        assertThat(post("/api/v1/college/prep/tests/" + testId + "/attempts/" + attemptId + "/extend", tpo, Map.of("minutes", 500)).status()).isEqualTo(400);
        assertThat(post("/api/v1/college/prep/tests/" + testId + "/attempts/" + attemptId + "/extend", otherTpo, Map.of("minutes", 5)).status()).isEqualTo(404);

        // submit: MCQs graded through the shuffle (4/4), written answer pending; results hidden from the student
        post(base + "/submit", student, Map.of());
        JsonNode res = get(base + "/result", student).data();
        assertThat(res.get("released").asBoolean()).isFalse();
        assertThat(res.get("score").isNull()).isTrue();
        assertThat(res.get("questions")).isEmpty();
        assertThat(get("/api/v1/college/student/prep/attempts", student).data().get(0).get("score").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select score from prep_attempts where id = ?", java.math.BigDecimal.class, attemptId)).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("select review_status from prep_attempts where id = ?", String.class, attemptId)).isEqualTo("PENDING");

        // one attempt only
        Resp again = post("/api/v1/college/student/prep/tests/" + testId + "/start", student, null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body().path("errorCode").asText()).isEqualTo("EXAM_ALREADY_TAKEN");
        // shuffling cannot change any more
        assertThat(put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", now.minusSeconds(60), now.plusSeconds(600), List.of(cse), false, false))
                .body().path("errorCode").asText()).isEqualTo("TEST_HAS_ATTEMPTS");
        assertThat(put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("PRACTICE", null, null, List.of(), true, false))
                .status()).isEqualTo(409);

        // teacher marks the written answer (0..5), score goes to 8.5
        String rv = "/api/v1/college/prep/tests/" + testId + "/attempts/" + attemptId;
        JsonNode review = get(rv + "/review", tpo).data();
        assertThat(review.path("result").get("pendingReview").asBoolean()).isTrue();
        assertThat(put(rv + "/marks", tpo, Map.of(textQ, 6)).body().path("errorCode").asText()).isEqualTo("INVALID_MARKS");
        assertThat(put(rv + "/marks", tpo, Map.of("q1", 1)).status()).isEqualTo(400);
        JsonNode marked = put(rv + "/marks", tpo, Map.of(textQ, 4.5)).data();
        assertThat(marked.path("result").get("score").decimalValue()).isEqualByComparingTo("8.5");
        assertThat(marked.path("result").get("pendingReview").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select review_status from prep_attempts where id = ?", String.class, attemptId)).isEqualTo("DONE");
        assertThat(get(rv + "/review", otherTpo).status()).isEqualTo(404);

        // release results, answers hidden: the student sees the score but not the correct options
        put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", now.minusSeconds(60), now.plusSeconds(600), List.of(cse), true, true));
        JsonNode released = get(base + "/result", student).data();
        assertThat(released.get("released").asBoolean()).isTrue();
        assertThat(released.get("score").decimalValue()).isEqualByComparingTo("8.5");
        assertThat(released.get("answersShown").asBoolean()).isFalse();
        for (JsonNode q : released.get("questions")) assertThat(q.path("correctOption").isMissingNode() || q.get("correctOption").isNull()).isTrue();

        // marks register: header + one row with roll number and score
        MockHttpServletResponse csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/college/prep/tests/" + testId + "/export").header("Authorization", "Bearer " + tpo)).andReturn().getResponse();
        String body = csv.getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.getStatus()).isEqualTo(200);
        assertThat(body).contains("Roll number,Name").contains(sCse.get("rollNumber").asText()).contains("8.5");
        assertThat(get("/api/v1/college/prep/tests/" + testId + "/monitor", student).status()).isEqualTo(403);
    }

    @Test
    void examWindowAndPracticeStillRetakes() throws Exception {
        String tpo = registerCollege("exw");
        String branch = addBranch(tpo, "IT");
        String student = activate(addStudent(tpo, branch, "W" + uid(), 7.5, 0).get("email").asText());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String upcoming = upload(tpo, EXAM_JSON.replace("Sessional 1", "Upcoming exam"));
        put("/api/v1/college/prep/tests/" + upcoming + "/settings", tpo, settings("EXAM", now.plusSeconds(3600), now.plusSeconds(7200), List.of(), false, false));
        patch("/api/v1/college/prep/tests/" + upcoming + "/publish", tpo, Map.of("published", true));
        assertThat(card(student, upcoming).get("examStatus").asText()).isEqualTo("UPCOMING");
        assertThat(post("/api/v1/college/student/prep/tests/" + upcoming + "/start", student, null).body().path("errorCode").asText()).isEqualTo("EXAM_NOT_STARTED");

        String closed = upload(tpo, EXAM_JSON.replace("Sessional 1", "Closed exam"));
        put("/api/v1/college/prep/tests/" + closed + "/settings", tpo, settings("EXAM", now.minusSeconds(7200), now.minusSeconds(3600), List.of(), false, false));
        patch("/api/v1/college/prep/tests/" + closed + "/publish", tpo, Map.of("published", true));
        assertThat(card(student, closed).get("examStatus").asText()).isEqualTo("CLOSED");
        assertThat(post("/api/v1/college/student/prep/tests/" + closed + "/start", student, null).body().path("errorCode").asText()).isEqualTo("EXAM_CLOSED");

        // a practice test: no window, retakes allowed, answers shown, results visible
        String practice = upload(tpo, EXAM_JSON.replace("Sessional 1", "Practice"));
        patch("/api/v1/college/prep/tests/" + practice + "/publish", tpo, Map.of("published", true));
        JsonNode pc = card(student, practice);
        assertThat(pc.get("mode").asText()).isEqualTo("PRACTICE");
        assertThat(pc.get("examStatus").isNull()).isTrue();
        String a1 = post("/api/v1/college/student/prep/tests/" + practice + "/start", student, null).data().get("attemptId").asText();
        post("/api/v1/college/student/prep/attempts/" + a1 + "/submit", student, Map.of());
        JsonNode r1 = get("/api/v1/college/student/prep/attempts/" + a1 + "/result", student).data();
        assertThat(r1.get("released").asBoolean()).isTrue();
        assertThat(r1.get("answersShown").asBoolean()).isTrue();
        Resp retake = post("/api/v1/college/student/prep/tests/" + practice + "/start", student, null);
        assertThat(retake.status()).isEqualTo(200);
        assertThat(retake.data().get("attemptId").asText()).isNotEqualTo(a1);
    }

    @Autowired private com.proveyu.student.prep.application.TestNotifier notifier;

    @Test
    void notificationsAndLiveAtStartTimeAndAttemptFilter() throws Exception {
        String tpo = registerCollege("exn");
        String cse = addBranch(tpo, "CSE");
        String ece = addBranch(tpo, "ECE");
        JsonNode a = addStudent(tpo, cse, "N" + uid(), 8.0, 0);
        JsonNode b = addStudent(tpo, ece, "M" + uid(), 8.0, 0);
        String student = activate(a.get("email").asText());
        activate(b.get("email").asText());
        String userA = jdbc.queryForObject("select user_id from college_students where email = ?", String.class, a.get("email").asText());
        String userB = jdbc.queryForObject("select user_id from college_students where email = ?", String.class, b.get("email").asText());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // an exam for CSE that starts in the future: publishing tells CSE only
        String testId = upload(tpo, EXAM_JSON.replace("Sessional 1", "Notify exam"));
        put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", now.plusSeconds(3600), now.plusSeconds(7200), List.of(cse), false, false));
        patch("/api/v1/college/prep/tests/" + testId + "/publish", tpo, Map.of("published", true));
        assertThat(notes(userA, "Notify exam")).anyMatch(m -> m.startsWith("New exam from your college"));
        assertThat(notes(userB, "Notify exam")).isEmpty();

        // rescheduled to have started a minute ago: a reschedule notice, then the scheduler says it is live (once)
        put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", now.minusSeconds(60), now.plusSeconds(3600), List.of(cse), false, false));
        assertThat(notes(userA, "Notify exam")).anyMatch(m -> m.contains("rescheduled"));
        notifier.announceLiveExams();
        notifier.announceLiveExams();
        assertThat(notes(userA, "Notify exam").stream().filter(m -> m.contains("is live now")).count()).isEqualTo(1);
        assertThat(card(student, testId).get("examStatus").asText()).isEqualTo("LIVE");

        // take it: extra time and results released are told to the student
        String attemptId = post("/api/v1/college/student/prep/tests/" + testId + "/start", student, null).data().get("attemptId").asText();
        post("/api/v1/college/prep/tests/" + testId + "/attempts/" + attemptId + "/extend", tpo, Map.of("minutes", 5));
        assertThat(notes(userA, "Notify exam")).anyMatch(m -> m.contains("5 extra minutes"));
        post("/api/v1/college/student/prep/attempts/" + attemptId + "/submit", student, Map.of());
        assertThat(put("/api/v1/college/prep/tests/" + testId + "/settings", tpo, settings("EXAM", now.minusSeconds(60), now.plusSeconds(3600), List.of(cse), false, true))
                .status()).isEqualTo(200);
        assertThat(notes(userA, "Notify exam")).anyMatch(m -> m.startsWith("Results of"));

        // attempts list filtered by test
        String other = upload(tpo, EXAM_JSON.replace("Sessional 1", "Other practice"));
        patch("/api/v1/college/prep/tests/" + other + "/publish", tpo, Map.of("published", true));
        String o1 = post("/api/v1/college/student/prep/tests/" + other + "/start", student, null).data().get("attemptId").asText();
        post("/api/v1/college/student/prep/attempts/" + o1 + "/submit", student, Map.of());
        JsonNode all = get("/api/v1/college/prep/attempts", tpo).data();
        JsonNode one = get("/api/v1/college/prep/attempts?testId=" + testId, tpo).data();
        assertThat(all.size()).isEqualTo(2);
        assertThat(one.size()).isEqualTo(1);
        assertThat(one.get(0).get("testId").asText()).isEqualTo(testId);

        // unpublishing tells the students it was withdrawn
        patch("/api/v1/college/prep/tests/" + other + "/publish", tpo, Map.of("published", false));
        assertThat(notes(userB, "Other practice")).anyMatch(m -> m.contains("withdrawn"));
    }

    private List<String> notes(String userId, String about) {
        return jdbc.queryForList("select message from notifications where user_id = ? and type = 'COLLEGE_TEST' and message like ?", String.class,
                userId, "%" + about + "%");
    }

    private Map<String, Object> settings(String mode, Instant start, Instant end, List<String> branches, boolean shuffleOptions, boolean released) {
        Map<String, Object> m = new HashMap<>();
        m.put("mode", mode);
        m.put("startsAt", start == null ? null : start.toString());
        m.put("endsAt", end == null ? null : end.toString());
        m.put("branchIds", branches);
        m.put("batchYears", List.of());
        m.put("shuffleQuestions", true);
        m.put("shuffleOptions", shuffleOptions);
        m.put("showAnswers", false);
        m.put("resultsReleased", released);
        return m;
    }

    private String upload(String tpo, String testJson) throws Exception {
        Resp up = call(multipart("/api/v1/college/prep/tests")
                .file(new MockMultipartFile("file", "t.json", "application/json", testJson.getBytes(StandardCharsets.UTF_8))), tpo, null);
        assertThat(up.status()).as(up.body().toString()).isEqualTo(200);
        return up.data().get("id").asText();
    }

    private JsonNode card(String token, String testId) throws Exception {
        for (JsonNode c : get("/api/v1/college/student/prep/tests", token).data()) if (c.get("id").asText().equals(testId)) return c;
        return null;
    }

    private String activate(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return login(email, "Student123");
    }
}
