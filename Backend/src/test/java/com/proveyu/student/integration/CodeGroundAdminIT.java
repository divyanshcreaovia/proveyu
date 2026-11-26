package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.student.codeground.CodeGroundContent;
import com.proveyu.student.codeground.CodeGroundStarters;
import com.proveyu.student.prep.application.CodeExecutionService;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/** Code ground content managed by a platform admin, and the problem of the day. The judge is faked. */
@TestPropertySource(properties = {"code.execution.rate-limit-seconds=0", "code.ground.leaderboard-cache-seconds=0"})
class CodeGroundAdminIT extends IntegrationTestBase {

    private static final String ADMIN = "/api/v1/admin/code-ground";
    private static final String STUDENT = "/api/v1/college/student/code-ground";

    @MockBean private CodeExecutionService runner;
    @Autowired private CodeGroundContent content;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void removeAdminContent() {
        for (String table : List.of("code_ground_problems", "code_ground_topics", "code_ground_hidden", "code_ground_schedule")) {
            jdbc.update("delete from " + table);
        }
        content.refresh();
    }

    @Test
    void startersForAdminProblemsMatchTheBuiltInOnes() {
        int checked = 0;
        for (CodeGroundContent.Topic t : content.builtInTopics()) {
            for (CodeGroundContent.Problem p : t.problems()) {
                Map<String, String> made = CodeGroundStarters.starters(p.args(), p.returnType());
                p.starters().forEach((language, expected) -> assertThat(made.get(language)).as(p.key() + " " + language).isEqualTo(expected));
                assertThat(CodeGroundStarters.inputFormat(p.args())).as(p.key()).isEqualTo(p.inputFormat());
                checked++;
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(74);
    }

    @Test
    void anUploadedProblemIsADraftUntilTheReferenceSolutionPasses() throws Exception {
        String admin = admin();
        String student = student();
        JsonNode sample = json.readTree(get(ADMIN + "/sample", admin).body().toString());
        assertThat(sample.has("topics")).isTrue();
        assertThat(get(ADMIN, student).status()).isEqualTo(403);
        assertThat(post(ADMIN + "/upload", student, sample).status()).isEqualTo(403);

        assertThat(post(ADMIN + "/upload", admin, sample).status()).isEqualTo(200);
        assertThat(get(STUDENT + "/problems/max-two-sum", student).status()).isEqualTo(404);       // draft
        assertThat(get(STUDENT, student).data().get("topics")).hasSize(10);

        judge(sample, "wrong");                                                                      // wrong answers: not published
        JsonNode refused = post(ADMIN + "/problems/max-two-sum/publish", admin, null).data();
        assertThat(refused.get("ok").asBoolean()).isFalse();
        assertThat(refused.get("failures")).hasSize(4);
        assertThat(get(STUDENT + "/problems/max-two-sum", student).status()).isEqualTo(404);

        judge(sample, null);
        assertThat(post(ADMIN + "/problems/max-two-sum/publish", admin, null).data().get("ok").asBoolean()).isTrue();
        JsonNode overview = get(STUDENT, student).data();
        assertThat(overview.get("topics")).hasSize(11);
        assertThat(overview.get("topics").get(10).get("total").asInt()).isEqualTo(2);              // the problem and the guide
        JsonNode view = get(STUDENT + "/problems/max-two-sum", student).data();
        assertThat(view.get("starters").get("java").asText()).contains("static int solve(int[] nums)");
        assertThat(view.has("solution")).isFalse();
        assertThat(view.get("hiddenCount").asInt()).isEqualTo(2);
        assertThat(post(STUDENT + "/problems/max-two-sum/submit", student, Map.of("code", "x")).data().get("allPassed").asBoolean()).isTrue();
        assertThat(get(STUDENT + "/leaderboard?period=all", student).data().get("me").get("points").asInt()).isGreaterThanOrEqualTo(10);

        // worked on: it cannot be deleted, only unpublished, and the points stay
        assertThat(delete(ADMIN + "/problems/max-two-sum", admin).status()).isEqualTo(400);
        post(ADMIN + "/problems/max-two-sum/unpublish", admin, null);
        assertThat(get(STUDENT + "/problems/max-two-sum", student).status()).isEqualTo(404);
        assertThat(get(STUDENT + "/leaderboard?period=all", student).data().get("me").get("points").asInt()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void mistakesAreExplainedAndNothingIsSaved() throws Exception {
        String admin = admin();
        ObjectNode file = (ObjectNode) json.readTree(get(ADMIN + "/sample", admin).body().toString());
        ObjectNode problem = (ObjectNode) file.get("topics").get(0).get("problems").get(0);
        ((ObjectNode) problem.get("tests").get(0)).put("input", "3 nine");
        ((ArrayNode) problem.get("args")).removeAll().addObject().put("name", "new").put("type", "ints");
        Resp r = post(ADMIN + "/upload", admin, file);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().get("message").asText()).contains("reserved");
        assertThat(jdbc.queryForObject("select count(*) from code_ground_topics", Integer.class)).isZero();

        // a built-in key cannot be taken
        ObjectNode spec = (ObjectNode) json.readTree(get(ADMIN + "/sample", admin).body().toString()).get("topics").get(0).get("problems").get(0);
        spec.put("key", "sum-n").put("topicKey", "basics");
        assertThat(put(ADMIN + "/problems", admin, spec).body().get("message").asText()).contains("built-in");
        spec.put("key", "own-sum").put("topicKey", "basics");
        ((ObjectNode) spec.get("tests").get(1)).put("input", "5 five");
        assertThat(put(ADMIN + "/problems", admin, spec).body().get("message").asText()).contains("numbers");
    }

    @Test
    void aHiddenBuiltInProblemDisappearsAndComesBack() throws Exception {
        String admin = admin();
        String student = student();
        put(ADMIN + "/problems/sum-n/hidden", admin, Map.of("hidden", true));
        assertThat(get(STUDENT + "/problems/sum-n", student).status()).isEqualTo(404);
        put(ADMIN + "/problems/sum-n/hidden", admin, Map.of("hidden", false));
        assertThat(get(STUDENT + "/problems/sum-n", student).status()).isEqualTo(200);
        assertThat(put(ADMIN + "/problems/nope/hidden", admin, Map.of("hidden", true)).status()).isEqualTo(400);
    }

    @Test
    void theAdminChoosesTheProblemOfTheDay() throws Exception {
        String admin = admin();
        String student = student();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        String auto = get(STUDENT, student).data().get("daily").get("key").asText();
        assertThat(get(STUDENT, student).data().get("daily").get("key").asText()).isEqualTo(auto);   // stays the same
        String chosen = auto.equals("fizzbuzz") ? "sum-n" : "fizzbuzz";
        assertThat(put(ADMIN + "/daily/" + today, admin, Map.of("problemKey", chosen)).status()).isEqualTo(200);
        assertThat(get(STUDENT, student).data().get("daily").get("key").asText()).isEqualTo(chosen);

        put(ADMIN + "/daily/" + today.plusDays(2), admin, Map.of("problemKey", "two-sum"));
        JsonNode days = get(ADMIN + "/daily?days=5", admin).data();
        assertThat(days).hasSize(5);
        assertThat(days.get(0).get("auto").asBoolean()).isFalse();
        assertThat(days.get(1).get("problemKey").isNull()).isTrue();
        assertThat(days.get(2).get("problemKey").asText()).isEqualTo("two-sum");

        assertThat(put(ADMIN + "/daily/" + today.minusDays(1), admin, Map.of("problemKey", "two-sum")).status()).isEqualTo(400);
        assertThat(put(ADMIN + "/daily/" + today, admin, Map.of("problemKey", "nope")).status()).isEqualTo(400);
        assertThat(put(ADMIN + "/daily/" + today, student, Map.of("problemKey", chosen)).status()).isEqualTo(403);

        // today's choice is hidden: students get another one, not an error
        put(ADMIN + "/problems/" + chosen + "/hidden", admin, Map.of("hidden", true));
        assertThat(get(STUDENT, student).data().get("daily").get("key").asText()).isNotEqualTo(chosen);
    }

    /** Prints the expected output of each input in the file (or the given text instead); the untouched starters just run. */
    private void judge(JsonNode file, String instead) {
        Map<String, String> answers = new HashMap<>();
        JsonNode p = file.get("topics").get(0).get("problems").get(0);
        for (String list : List.of("tests", "hiddenTests")) {
            p.get(list).forEach(c -> answers.put(c.get("input").asText(), c.get("output").asText()));
        }
        org.mockito.Mockito.doAnswer(call -> new CodeExecutionService.Result(null,
                Optional.ofNullable(call.<List<String>>getArgument(2)).orElse(List.of()).stream()
                        .map(in -> new CodeExecutionService.Output((instead != null ? instead : answers.get(in)) + "\n", "", 0, false)).toList()))
                .when(runner).run(anyString(), anyString(), any());
    }

    private Resp delete(String url, String token) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), token, null);
    }

    private String admin() throws Exception {
        String email = "root" + uid() + "@proveyu.test";
        users.save(User.builder().email(email).fullName("Root Admin").passwordHash(encoder.encode("RootPass1")).role(Role.ADMIN).active(true)
                .emailVerifiedAt(Instant.now()).build());
        return login(email, "RootPass1");
    }

    private String student() throws Exception {
        String tpo = registerCollege("ca");
        String email = addStudent(tpo, addBranch(tpo, "CSE"), "A" + uid(), 8.0, 0).get("email").asText();
        String roll = jdbc.queryForObject("select roll_number from college_students where email = ?", String.class, email);
        var start = post("/api/v1/college/auth/student-signup", null, Map.of("email", email, "rollNumber", roll));
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", start.data().get("devOtp").asText(), "newPassword", "Student123"));
        return login(email, "Student123");
    }
}
