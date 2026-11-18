package com.proveyu.college.integration;

import com.proveyu.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** End-to-end: import -> drive with eligibility -> publish -> student applies -> rounds -> offer -> placed -> policy, plus team roles. */
class PlacementFlowIT extends IntegrationTestBase {

    @Test
    void fullPlacementFlow() throws Exception {
        String tpo = registerCollege("flow");
        String cse = addBranch(tpo, "CSE");
        addBranch(tpo, "ECE");

        // import: dry run validates, commit creates; duplicates detected
        String csv = "roll_number,first_name,email,phone,branch_code,batch_year,cgpa,active_backlogs,skills\n"
                + "F1,Asha,asha.f@x.in,9111111111,CSE,2027,8.6,0,Java;SQL\n"
                + "F2,Bala,bala.f@x.in,9111111112,CSE,2027,6.1,0,Java\n"
                + "F2,Dup,dup.f@x.in,9111111113,CSE,2027,7,0,\n"
                + "F3,Bad,not-an-email,,ZZZ,2027,8,0,\n";
        MockMultipartFile file = new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        Resp dry = call(multipart("/api/v1/college/students/import").file(file).param("dryRun", "true"), tpo, null);
        assertThat(dry.data().get("validRows").asInt()).isEqualTo(2);
        assertThat(dry.data().get("duplicateRows").asInt()).isEqualTo(1);
        assertThat(dry.data().get("errorRows").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/college/students", tpo).data().get("totalElements").asInt()).as("dry run writes nothing").isZero();
        Resp commit = call(multipart("/api/v1/college/students/import").file(file).param("dryRun", "false"), tpo, null);
        assertThat(commit.data().get("created").asInt()).isEqualTo(2);
        JsonNode asha = get("/api/v1/college/students?q=asha.f", tpo).data().get("content").get(0);

        // company + drive with eligibility
        String company = post("/api/v1/college/companies", tpo, Map.of("name", "Flow " + uid())).data().get("id").asText();
        String drive = post("/api/v1/college/drives", tpo, Map.of("companyId", company, "title", "Flow drive", "roleTitle", "SDE", "driveType", "ON_CAMPUS",
                "ctcLpa", 9, "registrationDeadline", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                "eligibility", Map.of("minCgpa", 7, "branchIds", List.of(cse)))).data().get("id").asText();
        JsonNode preview = get("/api/v1/college/drives/" + drive + "/eligibility", tpo).data();
        assertThat(preview.get("eligible").asInt()).isEqualTo(1);
        assertThat(preview.get("ineligible").asInt()).isEqualTo(1);

        // student cannot apply to a draft; publish; then apply
        String student = setStudentPassword(asha.get("email").asText());
        assertThat(post("/api/v1/college/student/drives/" + drive + "/apply", student, null).status()).isEqualTo(409);
        assertThat(post("/api/v1/college/drives/" + drive + "/status", tpo, Map.of("status", "PUBLISHED")).status()).isEqualTo(200);
        assertThat(post("/api/v1/college/student/drives/" + drive + "/apply", student, null).status()).isEqualTo(200);

        // ineligible student (Bala, CGPA 6.1) cannot apply
        JsonNode bala = get("/api/v1/college/students?q=bala.f", tpo).data().get("content").get(0);
        String balaToken = setStudentPassword(bala.get("email").asText());
        Resp denied = post("/api/v1/college/student/drives/" + drive + "/apply", balaToken, null);
        assertThat(denied.status()).isEqualTo(422);
        assertThat(denied.body().path("code").asText()).isEqualTo("NOT_ELIGIBLE");

        // shortlist, two rounds, auto-advance to SELECTED
        String app = get("/api/v1/college/drives/" + drive + "/applications", tpo).data().get("content").get(0).get("id").asText();
        patch("/api/v1/college/applications/status", tpo, Map.of("applicationIds", List.of(app), "status", "SHORTLISTED"));
        String r1 = post("/api/v1/college/drives/" + drive + "/rounds", tpo, Map.of("name", "Aptitude", "roundType", "APTITUDE")).data().get("id").asText();
        String r2 = post("/api/v1/college/drives/" + drive + "/rounds", tpo, Map.of("name", "HR", "roundType", "HR")).data().get("id").asText();
        assertThat(post("/api/v1/college/rounds/" + r1 + "/start", tpo, null).status()).isEqualTo(200);
        put("/api/v1/college/rounds/" + r1 + "/results", tpo, Map.of("results", List.of(Map.of("applicationId", app, "result", "PASS"))));
        assertThat(post("/api/v1/college/rounds/" + r1 + "/complete", tpo, null).status()).isEqualTo(200);
        post("/api/v1/college/rounds/" + r2 + "/start", tpo, null);
        put("/api/v1/college/rounds/" + r2 + "/results", tpo, Map.of("results", List.of(Map.of("applicationId", app, "result", "PASS"))));
        post("/api/v1/college/rounds/" + r2 + "/complete", tpo, null);
        assertThat(get("/api/v1/college/drives/" + drive + "/applications", tpo).data().get("content").get(0).get("status").asText()).isEqualTo("SELECTED");

        // offer -> student accepts -> PLACED; dashboard reflects it
        String offer = post("/api/v1/college/offers", tpo, Map.of("studentId", asha.get("id").asText(), "driveId", drive, "companyId", company,
                "roleTitle", "SDE", "ctcLpa", 9)).data().get("id").asText();
        assertThat(post("/api/v1/college/student/offers/" + offer + "/respond", student, Map.of("accept", true)).data().get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(get("/api/v1/college/students/" + asha.get("id").asText(), tpo).data().get("student").get("status").asText()).isEqualTo("PLACED");
        assertThat(get("/api/v1/college/analytics/dashboard", tpo).data().get("kpis").get("placedStudents").asInt()).isEqualTo(1);

        // dream policy: a second Dream-tier offer is blocked unless overridden with a reason
        Resp blocked = post("/api/v1/college/offers", tpo, Map.of("studentId", asha.get("id").asText(), "companyId", company, "roleTitle", "SDE2", "ctcLpa", 9.5));
        assertThat(blocked.status()).isEqualTo(422);
        assertThat(blocked.body().path("code").asText()).isEqualTo("POLICY_VIOLATION");
        assertThat(post("/api/v1/college/offers", tpo, Map.of("studentId", asha.get("id").asText(), "companyId", company, "roleTitle", "Senior",
                "ctcLpa", 18)).status()).as("Super Dream upgrade allowed").isEqualTo(201);

        // revoking the accepted offer recomputes placement (another offer still OFFERED -> ACTIVE, not PLACED)
        patch("/api/v1/college/offers/" + offer + "/status", tpo, Map.of("status", "REVOKED", "remarks", "test"));
        assertThat(get("/api/v1/college/students/" + asha.get("id").asText(), tpo).data().get("student").get("status").asText()).isEqualTo("ACTIVE");

        // reports render
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/college/reports/placement-summary?format=pdf")
                .header("Authorization", "Bearer " + tpo)).andReturn().getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
    }

    @Test
    void teamRolesAndBranchScope() throws Exception {
        String tpo = registerCollege("team");
        String cse = addBranch(tpo, "CSE");
        String ece = addBranch(tpo, "ECE");
        String cseStudent = addStudent(tpo, cse, "T" + uid(), 8, 0).get("id").asText();
        String eceStudent = addStudent(tpo, ece, "T" + uid(), 8, 0).get("id").asText();

        Resp inv = post("/api/v1/college/team/invites", tpo, Map.of("email", "coord" + uid() + "@x.in", "fullName", "Coord", "role", "COLLEGE_COORDINATOR",
                "branchIds", List.of(cse)));
        String coord = post("/api/v1/college/auth/accept-invite", null, Map.of("token", inv.data().get("devInviteToken").asText(), "password", "Coord1234"))
                .data().get("token").asText();
        assertThat(get("/api/v1/college/students/" + cseStudent, coord).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/students/" + eceStudent, coord).status()).as("outside branch scope").isEqualTo(404);
        assertThat(get("/api/v1/college/students", coord).data().get("totalElements").asInt()).isEqualTo(1);
        assertThat(post("/api/v1/college/team/invites", coord, Map.of("email", "x" + uid() + "@x.in", "fullName", "X", "role", "COLLEGE_VIEWER")).status())
                .as("only admins invite").isEqualTo(403);
        assertThat(get("/api/v1/college/audit-logs", coord).status()).isEqualTo(403);

        // TPO cannot demote the last admin (themselves) and revocation takes effect immediately
        String coordMemberId = null;
        for (JsonNode m : get("/api/v1/college/team/members", tpo).data()) if (m.get("role").asText().equals("COLLEGE_COORDINATOR")) coordMemberId = m.get("id").asText();
        assertThat(post("/api/v1/college/team/members/" + coordMemberId + "/revoke", tpo, null).status()).isEqualTo(200);
        Resp revoked = get("/api/v1/college/students", coord);
        assertThat(revoked.status()).isEqualTo(403);
        assertThat(revoked.body().path("code").asText()).isEqualTo("ACCESS_REVOKED");

        // role change makes the old token stale
        post("/api/v1/college/team/members/" + coordMemberId + "/restore", tpo, null);
        put("/api/v1/college/team/members/" + coordMemberId, tpo, Map.of("role", "COLLEGE_VIEWER"));
        Resp stale = get("/api/v1/college/students", coord);
        assertThat(stale.status()).isEqualTo(401);
        assertThat(stale.body().path("code").asText()).isEqualTo("SESSION_STALE");
    }

    private String setStudentPassword(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return login(email, "Student123");
    }
}
