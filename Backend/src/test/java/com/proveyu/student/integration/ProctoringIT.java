package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
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
