package com.proveyu.student.integration;

import com.proveyu.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Student portal: sign-up/activation, announcement inbox, chat contacts by branch scope, document locker ownership, profile edit. */
class StudentPortalIT extends IntegrationTestBase {

    @Test
    void studentPortalFlow() throws Exception {
        String tpo = registerCollege("stu");
        String cse = addBranch(tpo, "CSE");
        String ece = addBranch(tpo, "ECE");
        String roll = "S" + uid();
        String email = addStudent(tpo, cse, roll, 8.2, 0).get("email").asText();
        String otherEmail = addStudent(tpo, ece, "O" + uid(), 7.5, 0).get("email").asText();

        // sign-up: e-mail + roll must match; the code proves the mailbox; then the student signs in
        assertThat(post("/api/v1/college/auth/student-signup", null, Map.of("email", email, "rollNumber", "WRONG")).status()).isEqualTo(404);
        Resp start = post("/api/v1/college/auth/student-signup", null, Map.of("email", email, "rollNumber", roll.toLowerCase()));
        assertThat(start.status()).isEqualTo(200);
        assertThat(post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", start.data().get("devOtp").asText(),
                "newPassword", "Student123")).status()).isEqualTo(200);
        String student = login(email, "Student123");
        String other = login(otherEmail, activate(otherEmail));

        // announcement to CSE only reaches the CSE student's inbox; read state is tracked both sides
        post("/api/v1/college/announcements", tpo, Map.of("title", "Drive tomorrow", "body", "Be on time", "filter", Map.of("branchIds", List.of(cse)),
                "sendEmail", false));
        JsonNode inbox = get("/api/v1/college/student/announcements", student).data().get("content");
        assertThat(inbox).hasSize(1);
        assertThat(get("/api/v1/college/student/profile", student).data().get("unreadAnnouncements").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/college/student/announcements", other).data().get("content")).isEmpty();
        assertThat(post("/api/v1/college/student/announcements/" + inbox.get(0).get("id").asText() + "/read", student, null).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/student/profile", student).data().get("unreadAnnouncements").asInt()).isZero();
        assertThat(get("/api/v1/college/announcements", tpo).data().get("content").get(0).get("readCount").asInt()).isEqualTo(1);

        // chat contacts: TPO for everyone; a CSE-scoped coordinator only for CSE students
        Resp inv = post("/api/v1/college/team/invites", tpo, Map.of("email", "coord" + uid() + "@x.in", "fullName", "Coord CSE",
                "role", "COLLEGE_COORDINATOR", "branchIds", List.of(cse)));
        post("/api/v1/college/auth/accept-invite", null, Map.of("token", inv.data().get("devInviteToken").asText(), "password", "Coord1234"));
        assertThat(get("/api/v1/college/student/chat/contacts", student).data()).hasSize(2);
        assertThat(get("/api/v1/college/student/chat/contacts", other).data()).hasSize(1);

        // document locker: upload with a label, download own file, other students cannot see it, verified files cannot be deleted
        MockMultipartFile pdf = new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
        Resp up = call(multipart("/api/v1/college/student/documents").file(pdf).param("docType", "CERTIFICATE").param("label", "AWS"), student, null);
        assertThat(up.status()).isEqualTo(200);
        String doc = up.data().get("id").asText();
        assertThat(up.data().get("label").asText()).isEqualTo("AWS");
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/college/student/documents/" + doc + "/file")
                .header("Authorization", "Bearer " + student)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/college/student/documents/" + doc + "/file")
                .header("Authorization", "Bearer " + other)).andReturn().getResponse().getStatus()).as("not the owner").isEqualTo(404);
        patch("/api/v1/college/students/documents/" + doc + "/verify", tpo, Map.of("status", "VERIFIED"));
        assertThat(call(delete("/api/v1/college/student/documents/" + doc), student, null).status()).isEqualTo(409);

        // profile: contact fields editable, bad URL rejected
        assertThat(patch("/api/v1/college/student/profile", student, Map.of("phone", "9876543210", "skills", List.of("Java", "SQL"),
                "linkedinUrl", "https://linkedin.com/in/x")).data().get("skills")).hasSize(2);
        assertThat(patch("/api/v1/college/student/profile", student, Map.of("linkedinUrl", "javascript:alert(1)")).status()).isEqualTo(400);

        // staff cannot use the student API
        assertThat(get("/api/v1/college/student/profile", tpo).status()).isEqualTo(403);
    }

    private String activate(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return "Student123";
    }
}
