package com.proveyu.college.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Auth hardening: role-restricted self-registration, e-mail verification, OTP handling, role-based routes. */
class AuthSecurityIT extends IntegrationTestBase {

    @Test
    void publicRegistrationCannotCreatePrivilegedRoles() throws Exception {
        for (String role : new String[]{"ADMIN", "COLLEGE_ADMIN", "ORGANIZATION"}) {
            Resp r = post("/api/v1/auth/register", null, Map.of("email", uid() + "@x.com", "password", "Secret123", "fullName", "X", "role", role));
            assertThat(r.status()).as(role).isEqualTo(400);
            assertThat(r.body().path("code").asText()).isEqualTo("ROLE_NOT_ALLOWED");
        }
    }

    @Test
    void collegeLoginRequiresVerifiedEmail() throws Exception {
        String domain = "v" + uid() + ".edu";
        Resp reg = post("/api/v1/college/auth/register", null, Map.ofEntries(Map.entry("collegeName", "V College"), Map.entry("aicteCode", "A-" + uid()),
                Map.entry("university", "U"), Map.entry("city", "C"), Map.entry("state", "S"), Map.entry("emailDomain", domain), Map.entry("tpoName", "T"),
                Map.entry("tpoPhone", "8" + String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L))), Map.entry("tpoEmail", "tpo@" + domain),
                Map.entry("password", "Secret123"), Map.entry("acceptTerms", true)));
        assertThat(reg.status()).isEqualTo(201);
        Resp blocked = post("/api/v1/auth/login", null, Map.of("email", "tpo@" + domain, "password", "Secret123"));
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.body().path("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(post("/api/v1/auth/verify-email", null, Map.of("token", reg.data().get("devVerificationToken").asText())).status()).isEqualTo(200);
        Resp ok = post("/api/v1/auth/login", null, Map.of("email", "tpo@" + domain, "password", "Secret123"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.data().get("role").asText()).isEqualTo("COLLEGE_ADMIN");
        assertThat(ok.data().get("collegeApprovalStatus").asText()).isEqualTo("APPROVED");
    }

    @Test
    void tpoEmailMustMatchCollegeDomain() throws Exception {
        Resp r = post("/api/v1/college/auth/register", null, Map.ofEntries(Map.entry("collegeName", "D College"), Map.entry("aicteCode", "D-" + uid()),
                Map.entry("university", "U"), Map.entry("city", "C"), Map.entry("state", "S"), Map.entry("emailDomain", "real.edu"), Map.entry("tpoName", "T"),
                Map.entry("tpoPhone", "7" + String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L))), Map.entry("tpoEmail", "tpo@gmail.com"),
                Map.entry("password", "Secret123"), Map.entry("acceptTerms", true)));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().path("code").asText()).isEqualTo("EMAIL_DOMAIN_MISMATCH");
    }

    @Test
    void otpResetFlowIsHashedSingleUseAndDoesNotEnumerate() throws Exception {
        Resp unknown = post("/api/v1/auth/forgot-password", null, Map.of("email", "nobody-" + uid() + "@x.com"));
        assertThat(unknown.status()).as("no account enumeration").isEqualTo(200);
        assertThat(unknown.data().has("devOtp")).isFalse();

        String email = uid() + "@cand.in";
        post("/api/v1/auth/register", null, Map.of("email", email, "password", "Secret123", "fullName", "C", "role", "CANDIDATE"));
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        assertThat(post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", "000000", "newPassword", "NewSecret1")).status()).isEqualTo(400);
        assertThat(post("/api/v1/auth/reset-password", null, Map.of("email", "other@x.com", "resetToken", otp, "newPassword", "NewSecret1")).status())
                .as("OTP is bound to the e-mail").isEqualTo(400);
        assertThat(post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "NewSecret1")).status()).isEqualTo(200);
        assertThat(post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Another1")).status())
                .as("single use").isEqualTo(400);
        assertThat(login(email, "NewSecret1")).isNotBlank();
    }

    @Test
    void routesAreLockedByRole() throws Exception {
        assertThat(get("/api/v1/college/students", null).status()).isEqualTo(401);
        assertThat(get("/api/v1/recruiter/jobs", null).status()).as("recruiter API no longer public").isEqualTo(401);
        assertThat(get("/api/v1/test/" + java.util.UUID.randomUUID(), null).status()).isEqualTo(401);

        String email = uid() + "@cand.in";
        String candidate = post("/api/v1/auth/register", null, Map.of("email", email, "password", "Secret123", "fullName", "C", "role", "CANDIDATE"))
                .data().get("token").asText();
        assertThat(get("/api/v1/college/students", candidate).status()).isEqualTo(403);
        assertThat(get("/api/v1/recruiter/jobs", candidate).status()).isEqualTo(403);
        assertThat(get("/api/v1/admin/colleges", candidate).status()).isEqualTo(403);
        assertThat(get("/api/v1/recruiter/jobs/candidate/invitations", candidate).status()).as("candidates keep their invitation API").isEqualTo(200);

        String tpo = registerCollege("r");
        assertThat(get("/api/v1/college/student/drives", tpo).status()).as("staff cannot use student portal").isEqualTo(403);
        Resp err = get("/api/v1/admin/colleges", tpo);
        assertThat(err.status()).isEqualTo(403);
        assertThat(err.body().path("status").asInt()).isEqualTo(403);
        assertThat(err.body().has("timestamp")).isTrue();
    }

    @Test
    void validationErrorsUseStandardBody() throws Exception {
        String tpo = registerCollege("v");
        Resp r = post("/api/v1/college/students", tpo, Map.of("rollNumber", "", "email", "bad"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(r.body().path("fieldErrors").has("email")).isTrue();
        assertThat(r.body().path("fieldErrors").has("branchId")).isTrue();
    }
}
