package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.student.codeground.CodeGroundContent;
import com.proveyu.student.prep.application.CodeExecutionService;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/** The leaderboard keeps a short cache per college; a student's own points must still show at once. */
@TestPropertySource(properties = {"code.execution.rate-limit-seconds=0", "code.ground.leaderboard-cache-seconds=600"})
class CodeGroundLeaderboardCacheIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/college/student/code-ground";

    @MockBean private CodeExecutionService runner;
    @Autowired private CodeGroundContent content;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void ownPointsShowAtOnceDespiteTheCache() throws Exception {
        String tpo = registerCollege("lc");
        String branch = addBranch(tpo, "CSE");
        String mine = activate(addStudent(tpo, branch, "P" + uid(), 8.0, 0).get("email").asText());
        String other = activate(addStudent(tpo, branch, "Q" + uid(), 8.0, 0).get("email").asText());

        // the board is read (and cached) while it is empty
        assertThat(get(BASE + "/leaderboard?period=all", other).data().get("participants").asInt()).isZero();

        post(BASE + "/topics/basics/guide/complete", mine, null);
        assertThat(get(BASE + "/leaderboard?period=all", mine).data().get("me").get("points").asInt()).as("after reading a guide").isEqualTo(5);

        var p = content.problem("count-digits").orElseThrow().problem();
        Map<String, String> answers = new HashMap<>();
        p.tests().forEach(c -> answers.put(c.input(), c.output()));
        p.hiddenTests().forEach(c -> answers.put(c.input(), c.output()));
        org.mockito.Mockito.doAnswer(call -> new CodeExecutionService.Result(null,
                Optional.ofNullable(call.<List<String>>getArgument(2)).orElse(List.of()).stream()
                        .map(in -> new CodeExecutionService.Output(answers.get(in) + "\n", "", 0, false)).toList()))
                .when(runner).run(anyString(), anyString(), any());
        post(BASE + "/problems/count-digits/submit", mine, Map.of("code", "x"));
        JsonNode me = get(BASE + "/leaderboard?period=all", mine).data().get("me");
        assertThat(me.get("points").asInt()).as("after solving an Easy problem").isGreaterThanOrEqualTo(15);
        assertThat(me.get("rank").asInt()).isEqualTo(1);
    }

    private String activate(String email) throws Exception {
        String roll = jdbc.queryForObject("select roll_number from college_students where email = ?", String.class, email);
        var start = post("/api/v1/college/auth/student-signup", null, Map.of("email", email, "rollNumber", roll));
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", start.data().get("devOtp").asText(), "newPassword", "Student123"));
        return login(email, "Student123");
    }
}
