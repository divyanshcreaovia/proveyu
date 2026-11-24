package com.proveyu.college.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With college.require-admin-approval=true (production): a newly registered college stays PENDING and cannot use the college
 * portal until a platform admin approves it; a rejected college stays locked out.
 */
@TestPropertySource(properties = "college.require-admin-approval=true")
class CollegeApprovalIT extends IntegrationTestBase {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void newCollegeWaitsForAdminApproval() throws Exception {
        String tpo = registerCollege("apr");
        String admin = admin();

        // signed in, but the portal is locked while PENDING
        assertThat(post("/api/v1/auth/login", null, Map.of("email", get("/api/v1/college/me", tpo).data().path("email").asText(), "password", "Secret123"))
                .data().get("collegeApprovalStatus").asText()).isEqualTo("PENDING");
        Resp students = get("/api/v1/college/students", tpo);
        assertThat(students.status()).isEqualTo(403);
        assertThat(students.body().path("errorCode").asText()).isEqualTo("COLLEGE_PENDING_APPROVAL");
        assertThat(post("/api/v1/college/settings/branches", tpo, Map.of("code", "CSE", "name", "CSE")).status()).isEqualTo(403);
        assertThat(get("/api/v1/college/me", tpo).data().get("approvalStatus").asText()).isEqualTo("PENDING");

        // the admin sees it waiting and approves it: the portal opens
        String collegeId = pendingCollege(admin, tpo);
        assertThat(collegeId).as("college listed as PENDING for the admin").isNotNull();
        assertThat(post("/api/v1/admin/colleges/" + collegeId + "/decision", admin, Map.of("status", "APPROVED")).status()).isEqualTo(200);
        assertThat(get("/api/v1/college/students", tpo).status()).isEqualTo(200);
        assertThat(post("/api/v1/college/settings/branches", tpo, Map.of("code", "CSE", "name", "CSE")).status()).isIn(200, 201);
    }

    @Test
    void rejectedCollegeStaysLockedOut() throws Exception {
        String tpo = registerCollege("rej");
        String admin = admin();
        String collegeId = pendingCollege(admin, tpo);
        assertThat(post("/api/v1/admin/colleges/" + collegeId + "/decision", admin, Map.of("status", "REJECTED", "note", "Not a real college")).status()).isEqualTo(200);
        Resp r = get("/api/v1/college/students", tpo);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body().path("errorCode").asText()).isEqualTo("COLLEGE_REJECTED");
    }

    private String pendingCollege(String admin, String tpo) throws Exception {
        String email = get("/api/v1/college/me", tpo).data().path("email").asText();
        for (JsonNode c : get("/api/v1/admin/colleges?status=PENDING", admin).data()) {
            if (c.get("tpoEmail").asText().equals(email) && c.get("approvalStatus").asText().equals("PENDING")) return c.get("collegeId").asText();
        }
        return null;
    }

    private String admin() throws Exception {
        String email = "root" + uid() + "@proveyu.test";
        users.save(User.builder().email(email).fullName("Root Admin").passwordHash(encoder.encode("RootPass1")).role(Role.ADMIN).active(true)
                .emailVerifiedAt(Instant.now()).build());
        return login(email, "RootPass1");
    }
}
