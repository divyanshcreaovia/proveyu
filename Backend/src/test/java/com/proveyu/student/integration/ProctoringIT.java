package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Mock test proctoring: leaving a running test is counted on the server; two warnings, the third switch submits the test.
 * Screenshots are proof for the student and the student's own placement cell only.
 */
class ProctoringIT extends IntegrationTestBase {

    /** Smallest bytes the server accepts as a JPEG (it checks the file signature). */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    private static final String TEST_JSON = """
            {"title": "Proctoring check", "durationMinutes": 30, "sections": [{"name": "Aptitude", "questions": [
              {"type": "MCQ", "text": "2 + 2 = ?", "options": ["3", "4", "5", "6"], "answer": 1},
              {"type": "MCQ", "text": "3 x 3 = ?", "options": ["6", "9", "12", "8"], "answer": 1}]}]}""";

    @Test
    void twoWarningsThenAutoSubmitWithProof() throws Exception {
        String tpo = registerCollege("proc");
        String branch = addBranch(tpo, "CSE");
        String student = activate(addStudent(tpo, branch, "P" + uid(), 8.0, 0).get("email").asText());
        String classmate = activate(addStudent(tpo, branch, "Q" + uid(), 8.0, 0).get("email").asText());
        String otherTpo = registerCollege("other");

        Resp up = call(multipart("/api/v1/college/prep/tests")
                .file(new MockMultipartFile("file", "t.json", "application/json", TEST_JSON.getBytes(StandardCharsets.UTF_8))), tpo, null);
        assertThat(up.status()).as(up.body().toString()).isEqualTo(200);
        String testId = up.data().get("id").asText();
        patch("/api/v1/college/prep/tests/" + testId + "/publish", tpo, Map.of("published", true));

        JsonNode attempt = post("/api/v1/college/student/prep/tests/" + testId + "/start", student, null).data();
        String attemptId = attempt.get("attemptId").asText();
        assertThat(attempt.get("tabSwitches").asInt()).isZero();
        assertThat(attempt.get("maxWarnings").asInt()).isEqualTo(2);
        String base = "/api/v1/college/student/prep/attempts/" + attemptId;

        // not an image: refused, and nothing is counted
        Resp bad = report(base, student, "TAB_HIDDEN", "not an image".getBytes(StandardCharsets.UTF_8));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().path("errorCode").asText()).isEqualTo("BAD_SCREENSHOT");

        // 1st switch: warning, 1 left
        JsonNode s1 = report(base, student, "TAB_HIDDEN", JPEG).data();
        assertThat(s1.get("tabSwitches").asInt()).isEqualTo(1);
        assertThat(s1.get("warningsLeft").asInt()).isEqualTo(1);
        assertThat(s1.get("autoSubmitted").asBoolean()).isFalse();
        assertThat(s1.path("event").get("hasScreenshot").asBoolean()).isTrue();
        // the same switch reported twice (blur + hidden) counts once
        assertThat(report(base, student, "WINDOW_BLUR", JPEG).data().get("tabSwitches").asInt()).isEqualTo(1);
        // another student cannot report on this attempt
        assertThat(report(base, classmate, "TAB_HIDDEN", JPEG).status()).isEqualTo(404);
        // a reload keeps the count
        assertThat(get_(base, student).data().get("tabSwitches").asInt()).isEqualTo(1);

        Thread.sleep(3200);
        JsonNode s2 = report(base, student, "WINDOW_BLUR", JPEG).data();
        assertThat(s2.get("tabSwitches").asInt()).isEqualTo(2);
        assertThat(s2.get("warningsLeft").asInt()).isZero();
        assertThat(s2.get("autoSubmitted").asBoolean()).isFalse();

        Thread.sleep(3200);
        JsonNode s3 = report(base, student, "SHARE_STOPPED", JPEG).data();
        assertThat(s3.get("tabSwitches").asInt()).isEqualTo(3);
        assertThat(s3.get("autoSubmitted").asBoolean()).as("3rd switch submits the test").isTrue();

        // the attempt is submitted and scored; later reports change nothing
        assertThat(get_(base + "/result", student).status()).isEqualTo(200);
        assertThat(get_(base, student).body().path("errorCode").asText()).isEqualTo("ATTEMPT_SUBMITTED");
        Thread.sleep(3200);
        assertThat(report(base, student, "TAB_HIDDEN", JPEG).data().get("tabSwitches").asInt()).isEqualTo(3);
        JsonNode history = get_("/api/v1/college/student/prep/attempts", student).data().get(0);
        assertThat(history.get("autoSubmitReason").asText()).isEqualTo("TAB_SWITCH");
        assertThat(history.get("tabSwitches").asInt()).isEqualTo(3);

        // student proof: the log and own screenshots; a classmate gets nothing
        JsonNode log = get_(base + "/proctor", student).data();
        assertThat(log.get("autoSubmitReason").asText()).isEqualTo("TAB_SWITCH");
        assertThat(log.get("events")).hasSize(3);
        assertThat(log.get("events").get(2).get("kind").asText()).isEqualTo("SHARE_STOPPED");
        String eventId = log.get("events").get(0).get("id").asText();
        MockHttpServletResponse img = image("/api/v1/college/student/prep/proctor/" + eventId + "/screenshot", student);
        assertThat(img.getStatus()).isEqualTo(200);
        assertThat(img.getContentType()).isEqualTo("image/jpeg");
        assertThat(img.getContentAsByteArray()).isEqualTo(JPEG);
        assertThat(image("/api/v1/college/student/prep/proctor/" + eventId + "/screenshot", classmate).getStatus()).isEqualTo(404);
        assertThat(get_(base + "/proctor", classmate).status()).isEqualTo(404);

        // placement cell of the student's college: attempts list (flagged), the log and the screenshot
        JsonNode flagged = get_("/api/v1/college/prep/attempts?flagged=true", tpo).data();
        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).get("attemptId").asText()).isEqualTo(attemptId);
        assertThat(flagged.get(0).get("tabSwitches").asInt()).isEqualTo(3);
        assertThat(flagged.get(0).get("autoSubmitReason").asText()).isEqualTo("TAB_SWITCH");
        assertThat(get_("/api/v1/college/prep/attempts/" + attemptId + "/proctor", tpo).data().get("events")).hasSize(3);
        assertThat(image("/api/v1/college/prep/proctor/" + eventId + "/screenshot", tpo).getStatus()).isEqualTo(200);

        // another college sees nothing; students cannot use the college view
        assertThat(get_("/api/v1/college/prep/attempts", otherTpo).data()).isEmpty();
        assertThat(get_("/api/v1/college/prep/attempts/" + attemptId + "/proctor", otherTpo).status()).isEqualTo(404);
        assertThat(image("/api/v1/college/prep/proctor/" + eventId + "/screenshot", otherTpo).getStatus()).isEqualTo(404);
        assertThat(get_("/api/v1/college/prep/attempts", student).status()).isEqualTo(403);
    }

    // ================================================================== AI proctoring (camera + microphone)

    @Autowired private JdbcTemplate jdbc;

    @Test
    void aiEventsNeedConsentAreRateLimitedFlagButNeverSubmit() throws Exception {
        String tpo = registerCollege("aip");
        String branch = addBranch(tpo, "CSE");
        String student = activate(addStudent(tpo, branch, "A" + uid(), 8.0, 0).get("email").asText());
        String classmate = activate(addStudent(tpo, branch, "B" + uid(), 8.0, 0).get("email").asText());
        String otherTpo = registerCollege("aio");
        String attemptId = startAttempt(tpo, student);
        String base = "/api/v1/college/student/prep/attempts/" + attemptId;

        JsonNode view = get_(base, student).data();
        assertThat(view.get("aiProctoring").asBoolean()).isTrue();
        assertThat(view.get("consentAt").isNull()).isTrue();
        assertThat(view.get("maxAiWarnings").asInt()).isEqualTo(5);

        // no consent yet: refused
        Resp noConsent = ai(base, student, "NO_FACE", null, null);
        assertThat(noConsent.status()).isEqualTo(403);
        assertThat(noConsent.body().path("errorCode").asText()).isEqualTo("CONSENT_REQUIRED");

        // consent is idempotent; another student cannot consent for this attempt
        assertThat(post(base + "/consent", classmate, null).status()).isEqualTo(404);
        String c1 = post(base + "/consent", student, null).data().get("consentAt").asText();
        String c2 = post(base + "/consent", student, null).data().get("consentAt").asText();
        assertThat(c1).isEqualTo(c2);

        // validation: unknown kind, a tab-switch kind, oversize snapshot, not an image, non-owner
        assertThat(ai(base, student, "DANCING", null, null).body().path("errorCode").asText()).isEqualTo("INVALID_KIND");
        assertThat(ai(base, student, "TAB_HIDDEN", null, null).body().path("errorCode").asText()).isEqualTo("INVALID_KIND");
        byte[] big = new byte[201 * 1024];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        Resp tooBig = ai(base, student, "NO_FACE", big, "image/jpeg");
        assertThat(tooBig.status()).isEqualTo(413);
        assertThat(tooBig.body().path("errorCode").asText()).isEqualTo("SNAPSHOT_TOO_LARGE");
        assertThat(ai(base, student, "NO_FACE", "nope".getBytes(StandardCharsets.UTF_8), "image/jpeg").body().path("errorCode").asText())
                .isEqualTo("BAD_SNAPSHOT");
        assertThat(ai(base, student, "NO_FACE", JPEG, "text/plain").body().path("errorCode").asText()).isEqualTo("BAD_SNAPSHOT");
        assertThat(ai(base, classmate, "NO_FACE", null, null).status()).isEqualTo(404);

        // a valid event is saved with its snapshot and metadata
        Resp first = call(multipart(base + "/proctor/ai").file(new MockMultipartFile("snapshot", "c.jpg", "image/jpeg", JPEG))
                .param("kind", "MULTIPLE_FACES").param("metadata", "{\"faces\":2}").param("clientAt", java.time.Instant.now().toString()), student, null);
        assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
        JsonNode s1 = first.data();
        assertThat(s1.get("recorded").asBoolean()).isTrue();
        assertThat(s1.get("aiWarnings").asInt()).isEqualTo(1);
        assertThat(s1.get("flagged").asBoolean()).isFalse();
        assertThat(s1.get("autoSubmitted").asBoolean()).isFalse();
        assertThat(s1.get("tabSwitches").asInt()).isZero();
        assertThat(s1.path("event").get("hasScreenshot").asBoolean()).isTrue();
        assertThat(s1.path("event").get("ai").asBoolean()).isTrue();
        assertThat(s1.path("event").get("metadata").asText()).isEqualTo("{\"faces\":2}");
        String eventId = s1.path("event").get("id").asText();
        assertThat(jdbc.queryForObject("select kind from prep_proctor_events where id = ?", String.class, eventId)).isEqualTo("MULTIPLE_FACES");

        // the same kind again within 10 s: no-op (200, not recorded, count unchanged)
        JsonNode again = ai(base, student, "MULTIPLE_FACES", JPEG, "image/jpeg").data();
        assertThat(again.get("recorded").asBoolean()).isFalse();
        assertThat(again.get("aiWarnings").asInt()).isEqualTo(1);
        // another kind is recorded, but its snapshot is dropped (one snapshot per 10 s)
        JsonNode voice = ai(base, student, "VOICE_DETECTED", JPEG, "image/jpeg").data();
        assertThat(voice.get("recorded").asBoolean()).isTrue();
        assertThat(voice.path("event").get("hasScreenshot").asBoolean()).isFalse();

        // AI events do not count as tab switches: one real switch is still only the 1st warning
        JsonNode sw = report(base, student, "TAB_HIDDEN", JPEG).data();
        assertThat(sw.get("tabSwitches").asInt()).isEqualTo(1);
        assertThat(sw.get("autoSubmitted").asBoolean()).isFalse();

        // over the AI limit (5): flagged, never submitted
        for (String k : new String[]{"NO_FACE", "LOOKING_AWAY", "CAMERA_BLOCKED"}) assertThat(ai(base, student, k, null, null).data().get("recorded").asBoolean()).isTrue();
        jdbc.update("update prep_proctor_events set occurred_at = occurred_at - interval 1 minute where attempt_id = ?", attemptId);
        JsonNode sixth = ai(base, student, "NO_FACE", null, null).data();
        assertThat(sixth.get("aiWarnings").asInt()).isEqualTo(6);
        assertThat(sixth.get("flagged").asBoolean()).isTrue();
        assertThat(sixth.get("autoSubmitted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select submitted_at from prep_attempts where id = ?", java.sql.Timestamp.class, attemptId)).isNull();
        assertThat(jdbc.queryForObject("select ai_warning_count from prep_attempts where id = ?", Integer.class, attemptId)).isEqualTo(6);

        // logs: the student and the own college see AI events with metadata; another college does not
        JsonNode log = get_(base + "/proctor", student).data();
        assertThat(log.get("flagged").asBoolean()).isTrue();
        assertThat(log.get("aiWarnings").asInt()).isEqualTo(6);
        assertThat(log.get("events")).hasSize(7);
        JsonNode collegeLog = get_("/api/v1/college/prep/attempts/" + attemptId + "/proctor", tpo).data();
        assertThat(collegeLog.get("events").get(0).get("kind").asText()).isEqualTo("MULTIPLE_FACES");
        assertThat(collegeLog.get("events").get(0).get("metadata").asText()).isEqualTo("{\"faces\":2}");
        assertThat(image("/api/v1/college/prep/proctor/" + eventId + "/screenshot", tpo).getStatus()).isEqualTo(200);
        assertThat(get_("/api/v1/college/prep/attempts/" + attemptId + "/proctor", otherTpo).status()).isEqualTo(404);
        JsonNode row = get_("/api/v1/college/prep/attempts?flagged=true", tpo).data();
        assertThat(row.findValues("attemptId").stream().map(JsonNode::asText)).contains(attemptId);

        // after submitting, AI events and consent are refused
        post(base + "/submit", student, Map.of());
        Resp late = ai(base, student, "LOOKING_AWAY", null, null);
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body().path("errorCode").asText()).isEqualTo("ATTEMPT_SUBMITTED");
        assertThat(post(base + "/consent", student, null).status()).isEqualTo(409);
    }

    private String startAttempt(String tpo, String student) throws Exception {
        Resp up = call(multipart("/api/v1/college/prep/tests")
                .file(new MockMultipartFile("file", "t.json", "application/json", TEST_JSON.getBytes(StandardCharsets.UTF_8))), tpo, null);
        String testId = up.data().get("id").asText();
        patch("/api/v1/college/prep/tests/" + testId + "/publish", tpo, Map.of("published", true));
        return post("/api/v1/college/student/prep/tests/" + testId + "/start", student, null).data().get("attemptId").asText();
    }

    private Resp ai(String base, String token, String kind, byte[] snapshot, String type) throws Exception {
        var req = multipart(base + "/proctor/ai");
        if (snapshot != null) req.file(new MockMultipartFile("snapshot", "c.jpg", type, snapshot));
        return call(req.param("kind", kind), token, null);
    }

    private Resp report(String base, String token, String kind, byte[] screenshot) throws Exception {
        return call(multipart(base + "/proctor").file(new MockMultipartFile("screenshot", "s.jpg", "image/jpeg", screenshot))
                .param("kind", kind).param("clientAt", java.time.Instant.now().toString()), token, null);
    }

    private Resp get_(String url, String token) throws Exception {
        return get(url, token);
    }

    private MockHttpServletResponse image(String url, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private String activate(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return login(email, "Student123");
    }
}
