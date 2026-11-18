package com.proveyu.college.integration;

import com.proveyu.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Drive Room: company HR works on one drive through the college services; both portals see the same data. */
class DriveRoomIT extends IntegrationTestBase {

    private static final String R = "/api/v1/recruiter/campus/drives/";
    private static final String LIST = "/api/v1/recruiter/campus/drives";

    @Test
    void companyRunsTheDriveAndEverythingStaysInSync() throws Exception {
        String tpo = registerCollege("room");
        String cse = addBranch(tpo, "CSE");
        JsonNode s1 = addStudent(tpo, cse, "A" + uid(), 8.5, 0);
        JsonNode s2 = addStudent(tpo, cse, "B" + uid(), 8.0, 0);
        String company = post("/api/v1/college/companies", tpo, Map.of("name", "Room Corp " + uid())).data().get("id").asText();
        String drive = newDrive(tpo, company, "Room drive");
        String otherDrive = newDrive(tpo, company, "Other drive");
        String r1 = post("/api/v1/college/drives/" + drive + "/rounds", tpo, Map.of("name", "Technical", "roundType", "TECHNICAL")).data().get("id").asText();
        String r2 = post("/api/v1/college/drives/" + drive + "/rounds", tpo, Map.of("name", "HR", "roundType", "HR")).data().get("id").asText();
        post("/api/v1/college/drives/" + drive + "/applications", tpo, Map.of("studentIds", List.of(s1.get("id").asText(), s2.get("id").asText())));

        // invite -> public info -> accept (new account)
        String hrEmail = "hr" + uid() + "@roomcorp.com";
        Resp inv = post("/api/v1/college/drives/" + drive + "/recruiters", tpo, Map.of("email", hrEmail, "fullName", "Riya HR", "shareDetails", false));
        assertThat(inv.status()).isEqualTo(200);
        String token = inv.data().get("devInviteToken").asText();
        JsonNode info = get("/api/v1/college/auth/recruiter-invite/" + token, null).data();
        assertThat(info.get("existingAccount").asBoolean()).isFalse();
        assertThat(info.get("driveTitle").asText()).isEqualTo("Room drive");
        Resp acc = post("/api/v1/college/auth/recruiter-accept", null, Map.of("token", token, "fullName", "Riya HR", "password", "Recruit123"));
        assertThat(acc.data().get("role").asText()).isEqualTo("RECRUITER");
        String hr = acc.data().get("token").asText();
        assertThat(post("/api/v1/college/auth/recruiter-accept", null, Map.of("token", token, "password", "Recruit123")).status()).as("token is single use").isEqualTo(404);

        // only the invited drive is visible; contact details hidden until shared
        assertThat(get(LIST, hr).data()).hasSize(1);
        assertThat(get(R + otherDrive, hr).status()).isEqualTo(404);
        JsonNode apps = get(R + drive + "/applicants", hr).data();
        assertThat(apps).hasSize(2);
        assertThat(apps.get(0).has("email")).isFalse();
        String accessId = inv.data().get("id").asText();
        patch("/api/v1/college/drives/" + drive + "/recruiters/" + accessId, tpo, Map.of("shareDetails", true));
        assertThat(get(R + drive + "/applicants", hr).data().get(0).get("email").asText()).contains("@");

        // role walls
        String student = activate(s1.get("email").asText());
        assertThat(get(LIST, student).status()).as("students cannot use the company API").isEqualTo(403);
        assertThat(get("/api/v1/college/students", hr).status()).as("company cannot use the college API").isEqualTo(403);

        // company shortlists -> TPO and student see it
        String a1 = appOf(apps, s1), a2 = appOf(apps, s2);
        assertThat(patch(R + drive + "/applications/status", hr, Map.of("applicationIds", List.of(a1, a2), "status", "SELECTED")).status())
                .as("SELECTED comes from round results only").isEqualTo(400);
        assertThat(patch(R + drive + "/applications/status", hr, Map.of("applicationIds", List.of(a1, a2), "status", "SHORTLISTED")).data().get("updated").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/college/drives/" + drive + "/applications", tpo).data().get("content").get(0).get("status").asText()).isEqualTo("SHORTLISTED");
        assertThat(get("/api/v1/college/student/applications", student).data().get(0).get("status").asText()).isEqualTo("SHORTLISTED");

        // round 1 with panel scorecards -> shared round results
        assertThat(post(R + drive + "/rounds/" + r1 + "/start", hr, null).status()).isEqualTo(200);
        assertThat(put(R + drive + "/rounds/" + r1 + "/scorecards/" + a1, hr, Map.of("technical", 4, "communication", 4, "attitude", 5, "result", "PASS", "remarks", "Strong DSA")).status()).isEqualTo(200);
        put(R + drive + "/rounds/" + r1 + "/scorecards/" + a2, hr, Map.of("technical", 2, "communication", 3, "attitude", 3, "result", "FAIL"));
        JsonNode tpoResults = get("/api/v1/college/rounds/" + r1 + "/results", tpo).data();
        JsonNode row1 = tpoResults.get(0).get("applicationId").asText().equals(a1) ? tpoResults.get(0) : tpoResults.get(1);
        assertThat(row1.get("result").asText()).isEqualTo("PASS");
        assertThat(row1.get("score").asDouble()).isEqualTo(4.33);
        assertThat(row1.get("remarks").asText()).contains("Riya HR").contains("Strong DSA");
        assertThat(get("/api/v1/college/rounds/" + r1 + "/scorecards", tpo).data()).hasSize(2);
        assertThat(post(R + drive + "/rounds/" + r1 + "/complete", hr, null).status()).isEqualTo(200);

        // round 2 with plain results -> SELECTED
        post(R + drive + "/rounds/" + r2 + "/start", hr, null);
        put(R + drive + "/rounds/" + r2 + "/results", hr, Map.of("results", List.of(Map.of("applicationId", a1, "result", "PASS"))));
        post(R + drive + "/rounds/" + r2 + "/complete", hr, null);
        JsonNode after = get(R + drive + "/applicants", hr).data();
        assertThat(statusOf(after, a1)).isEqualTo("SELECTED");
        assertThat(statusOf(after, a2)).isEqualTo("REJECTED");

        // offers through the policy engine; only SELECTED students get one
        JsonNode offers = post(R + drive + "/offers", hr, Map.of("applicationIds", List.of(a1, a2), "ctcLpa", 9)).data();
        assertThat(offers.get("created").asInt()).isEqualTo(1);
        assertThat(offers.get("errors").get(0).asText()).contains("not selected");
        assertThat(get("/api/v1/college/student/offers", student).data().get(0).get("status").asText()).isEqualTo("OFFERED");
        assertThat(get("/api/v1/college/offers?driveId=" + drive, tpo).data().get("content")).hasSize(1);
        String offerId = get(R + drive + "/offers", hr).data().get(0).get("id").asText();
        MockMultipartFile letter = new MockMultipartFile("file", "offer.pdf", "application/pdf", "%PDF-1.4 offer".getBytes(StandardCharsets.UTF_8));
        assertThat(call(multipart(R + drive + "/offers/" + offerId + "/letter").file(letter), hr, null).status()).isEqualTo(200);

        // audit shows the company person as the actor
        JsonNode audit = get("/api/v1/college/audit-logs?size=100", tpo).data().get("content");
        boolean companyActor = false;
        for (JsonNode e : audit) companyActor |= e.path("actorName").asText().contains("Riya HR (Room Corp");
        assertThat(companyActor).isTrue();

        // resumes zip + report
        assertThat(post(R + drive + "/resumes.zip", hr, null).status()).as("no resumes yet").isEqualTo(404);
        MockMultipartFile cv = new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 cv".getBytes(StandardCharsets.UTF_8));
        call(multipart("/api/v1/college/student/documents").file(cv).param("docType", "RESUME"), student, null);
        var zip = mvc.perform(MockMvcRequestBuilders.post(R + drive + "/resumes.zip").header("Authorization", "Bearer " + hr)).andReturn().getResponse();
        assertThat(zip.getStatus()).isEqualTo(200);
        assertThat(new String(zip.getContentAsByteArray(), 0, 2)).isEqualTo("PK");
        var report = mvc.perform(MockMvcRequestBuilders.get(R + drive + "/report?format=xlsx").header("Authorization", "Bearer " + hr)).andReturn().getResponse();
        assertThat(report.getStatus()).isEqualTo(200);

        // existing recruiter joins a second drive with their password; revoke closes the first
        String inv2 = post("/api/v1/college/drives/" + otherDrive + "/recruiters", tpo, Map.of("email", hrEmail, "fullName", "Riya HR")).data().get("devInviteToken").asText();
        assertThat(get("/api/v1/college/auth/recruiter-invite/" + inv2, null).data().get("existingAccount").asBoolean()).isTrue();
        assertThat(post("/api/v1/college/auth/recruiter-accept", null, Map.of("token", inv2, "password", "wrong-pass")).status()).isEqualTo(401);
        assertThat(post("/api/v1/college/auth/recruiter-accept", null, Map.of("token", inv2, "password", "Recruit123")).status()).isEqualTo(200);
        assertThat(get(LIST, hr).data()).hasSize(2);
        post("/api/v1/college/drives/" + drive + "/recruiters/" + accessId + "/revoke", tpo, null);
        assertThat(get(R + drive, hr).status()).isEqualTo(404);
        assertThat(get(LIST, hr).data()).hasSize(1);
    }

    private String newDrive(String tpo, String company, String title) throws Exception {
        String d = post("/api/v1/college/drives", tpo, Map.of("companyId", company, "title", title, "roleTitle", "SDE", "driveType", "ON_CAMPUS",
                "ctcLpa", 9, "registrationDeadline", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "eligibility", Map.of("minCgpa", 7))).data().get("id").asText();
        post("/api/v1/college/drives/" + d + "/status", tpo, Map.of("status", "PUBLISHED"));
        return d;
    }

    private String activate(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return login(email, "Student123");
    }

    private static String appOf(JsonNode apps, JsonNode student) {
        for (JsonNode a : apps) if (a.get("studentId").asText().equals(student.get("id").asText())) return a.get("applicationId").asText();
        throw new IllegalStateException("no application for " + student);
    }

    private static String statusOf(JsonNode apps, String appId) {
        for (JsonNode a : apps) if (a.get("applicationId").asText().equals(appId)) return a.get("status").asText();
        return "MISSING";
    }
}
