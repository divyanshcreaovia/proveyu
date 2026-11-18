package com.proveyu.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform admin portal: only admins get in, admins are created only by admins, blocking ends open sessions, settings apply at once,
 * colleges can be suspended and reactivated, broadcasts reach users, and every action lands in the admin audit log.
 */
class AdminPortalIT extends IntegrationTestBase {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    /** The first admin of a new installation (what AdminBootstrap does from ADMIN_BOOTSTRAP_*). */
    private String firstAdmin() throws Exception {
        String email = "root" + uid() + "@proveyu.test";
        users.save(User.builder().email(email).fullName("Root Admin").passwordHash(encoder.encode("RootPass1")).role(Role.ADMIN).active(true)
                .emailVerifiedAt(Instant.now()).build());
        return login(email, "RootPass1");
    }

    @Test
    void adminPortal() throws Exception {
        String admin = firstAdmin();
        String tpo = registerCollege("adm");
        String branch = addBranch(tpo, "CSE");
        String roll = "A" + uid();
        String studentEmail = addStudent(tpo, branch, roll, 8.1, 0).get("email").asText();
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", studentEmail)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", studentEmail, "resetToken", otp, "newPassword", "Student123"));
        String student = login(studentEmail, "Student123");

        // ---- only admins
        assertThat(get("/api/v1/admin/dashboard", tpo).status()).isEqualTo(403);
        assertThat(get("/api/v1/admin/dashboard", student).status()).isEqualTo(403);
        assertThat(get("/api/v1/admin/dashboard", null).status()).isEqualTo(401);
        JsonNode dash = get("/api/v1/admin/dashboard", admin).data();
        assertThat(dash.get("students").asLong()).isPositive();
        assertThat(dash.has("ai")).isTrue();
        assertThat(dash.path("colleges").has("SUSPENDED")).isTrue();

        // ---- a new admin only from inside; no password hash ever returned
        String email2 = "second" + uid() + "@proveyu.test";
        assertThat(post("/api/v1/admin/admins", admin, Map.of("email", email2, "fullName", "Second", "password", "onlyletters")).status()).isEqualTo(400);
        Resp created = post("/api/v1/admin/admins", admin, Map.of("email", email2, "fullName", "Second Admin", "password", "Second123"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().toString()).doesNotContain("passwordHash").doesNotContain("$2a$");
        assertThat(post("/api/v1/admin/admins", admin, Map.of("email", email2, "fullName", "Again", "password", "Second123")).status()).isEqualTo(409);
        assertThat(post("/api/v1/admin/admins", tpo, Map.of("email", "x" + uid() + "@x.in", "fullName", "Nope", "password", "Nope12345")).status()).isEqualTo(403);
        String admin2 = login(email2, "Second123");
        assertThat(get("/api/v1/admin/admins", admin2).data().size()).isGreaterThanOrEqualTo(2);
        Resp legacy = post("/api/v1/admin/users/create-admin", admin, Map.of("email", "third" + uid() + "@proveyu.test", "fullName", "Third", "password", "Third1234"));
        assertThat(legacy.status()).isEqualTo(201);
        assertThat(legacy.body().toString()).doesNotContain("passwordHash");

        // ---- users: search finds the student with college and roll number
        JsonNode found = get("/api/v1/admin/users?q=" + roll.toLowerCase(), admin).data();
        assertThat(found.get("total").asLong()).isEqualTo(1);
        JsonNode row = found.get("rows").get(0);
        assertThat(row.get("email").asText()).isEqualTo(studentEmail);
        assertThat(row.get("detail").asText()).isEqualTo(roll);
        assertThat(row.get("collegeName").asText()).startsWith("College adm");
        String studentId = row.get("id").asText();

        // ---- block ends sign-in and the open session; unblock restores both
        assertThat(post("/api/v1/admin/users/" + studentId + "/block", admin, null).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/student/profile", student).status()).as("open session stops at once").isEqualTo(401);
        Resp refused = post("/api/v1/auth/login", null, Map.of("email", studentEmail, "password", "Student123"));
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body().path("errorCode").asText()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(post("/api/v1/admin/users/" + studentId + "/unblock", admin, null).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/student/profile", student).status()).isEqualTo(200);
        String adminId = get("/api/v1/admin/admins", admin).data().findValues("id").stream()
                .filter(n -> users.findById(java.util.UUID.fromString(n.asText())).map(u -> u.getFullName().equals("Root Admin")).orElse(false))
                .findFirst().orElseThrow().asText();
        assertThat(post("/api/v1/admin/users/" + adminId + "/block", admin, null).body().path("errorCode").asText()).isEqualTo("SELF_BLOCK");
        assertThat(post("/api/v1/admin/users/" + studentId + "/reset-password", admin, null).data().has("devOtp")).isTrue();

        // ---- settings apply at once and are checked
        assertThat(get("/api/v1/college/student/interview", student).data().path("credits").path("pricePerInterview").asInt()).isEqualTo(20);
        assertThat(put("/api/v1/admin/settings", admin, Map.of("interview.price-rupees", "35")).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/student/interview", student).data().path("credits").path("pricePerInterview").asInt()).isEqualTo(35);
        assertThat(put("/api/v1/admin/settings", admin, Map.of("interview.price-rupees", "0")).body().path("errorCode").asText()).isEqualTo("INVALID_SETTING");
        assertThat(put("/api/v1/admin/settings", admin, Map.of("interview.upi.id", "not a upi")).status()).isEqualTo(400);
        assertThat(put("/api/v1/admin/settings", admin, Map.of("no.such.key", "1")).body().path("errorCode").asText()).isEqualTo("UNKNOWN_SETTING");
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/settings/interview.price-rupees"), admin, null).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/student/interview", student).data().path("credits").path("pricePerInterview").asInt()).isEqualTo(20);
        put("/api/v1/admin/settings", admin, Map.of("site.banner.text", "Placement week starts Monday"));
        JsonNode banner = get("/api/v1/public/banner", null).data();
        assertThat(banner.get("text").asText()).isEqualTo("Placement week starts Monday");

        // ---- suspend and reactivate a college
        String collegeId = null;
        JsonNode colleges = get("/api/v1/admin/colleges", admin).data();
        for (JsonNode c : colleges) if (c.get("tpoEmail").asText().equals(get("/api/v1/college/me", tpo).data().path("email").asText())) collegeId = c.get("collegeId").asText();
        assertThat(collegeId).isNotNull();
        JsonNode detail = get("/api/v1/admin/colleges/" + collegeId, admin).data();
        assertThat(detail.get("students").asLong()).isEqualTo(1);
        assertThat(detail.get("team").size()).isEqualTo(1);
        assertThat(post("/api/v1/admin/colleges/" + collegeId + "/decision", admin, Map.of("status", "SUSPENDED", "note", "Fees due")).status()).isEqualTo(200);
        Resp blocked = get("/api/v1/college/students", tpo);
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.body().path("errorCode").asText()).isEqualTo("COLLEGE_SUSPENDED");
        assertThat(post("/api/v1/admin/colleges/" + collegeId + "/decision", admin, Map.of("status", "APPROVED")).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/students", tpo).status()).isEqualTo(200);

        // ---- broadcast to one college reaches its student
        assertThat(get("/api/v1/admin/broadcast/count?audience=COLLEGE&collegeId=" + collegeId, admin).data().get("recipients").asInt()).isEqualTo(2);
        Resp sent = post("/api/v1/admin/broadcast", admin, Map.of("audience", "COLLEGE", "collegeId", collegeId, "title", "Maintenance",
                "message", "ProveYu is down on Sunday 2-3 am", "sendEmail", false));
        assertThat(sent.data().get("recipients").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/notifications", student).body().toString()).contains("Maintenance: ProveYu is down on Sunday 2-3 am");

        // ---- payments export, proctoring list
        var export = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/payments/export")
                .header("Authorization", "Bearer " + admin)).andReturn().getResponse();
        assertThat(export.getStatus()).isEqualTo(200);
        assertThat(export.getContentType()).contains("spreadsheetml");
        assertThat(get("/api/v1/admin/proctoring", admin).status()).isEqualTo(200);

        // ---- every action is in the admin audit log
        String log = get("/api/v1/admin/audit?size=100", admin).body().toString();
        for (String action : new String[]{"ADMIN_CREATED", "USER_BLOCKED", "USER_UNBLOCKED", "PASSWORD_RESET_SENT", "SETTINGS_CHANGED",
                "COLLEGE_SUSPENDED", "COLLEGE_APPROVED", "BROADCAST_SENT"}) {
            assertThat(log).as(action).contains(action);
        }
        assertThat(get("/api/v1/admin/audit", tpo).status()).isEqualTo(403);
    }
}
