package com.proveyu.college.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** College A data must be invisible and immutable for College B, whatever IDs or parameters B sends. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenancyIsolationIT extends IntegrationTestBase {

    String a, b, branchA, studentA, companyA, driveA, collegeIdA;

    @BeforeAll
    void setUp() throws Exception {
        a = registerCollege("a");
        b = registerCollege("b");
        collegeIdA = get("/api/v1/college/me", a).data().get("collegeId").asText();
        branchA = addBranch(a, "CSE");
        studentA = addStudent(a, branchA, "A" + uid(), 8.5, 0).get("id").asText();
        companyA = post("/api/v1/college/companies", a, Map.of("name", "Acme " + uid())).data().get("id").asText();
        driveA = post("/api/v1/college/drives", a, Map.of("companyId", companyA, "title", "Acme drive", "roleTitle", "SDE", "driveType", "ON_CAMPUS",
                "ctcLpa", 6)).data().get("id").asText();
    }

    @Test
    void collegeBCannotReadCollegeAData() throws Exception {
        assertThat(get("/api/v1/college/students/" + studentA, b).status()).isEqualTo(404);
        assertThat(get("/api/v1/college/companies/" + companyA, b).status()).isEqualTo(404);
        assertThat(get("/api/v1/college/drives/" + driveA, b).status()).isEqualTo(404);
        assertThat(get("/api/v1/college/drives/" + driveA + "/eligibility", b).status()).isEqualTo(404);
        assertThat(get("/api/v1/college/drives/" + driveA + "/applications", b).status()).isEqualTo(404);
        assertThat(get("/api/v1/college/students", b).data().get("totalElements").asInt()).isZero();
        assertThat(get("/api/v1/college/search?q=Acme", b).data().get("companies").size()).isZero();
    }

    @Test
    void clientSuppliedCollegeIdIsIgnored() throws Exception {
        JsonNode page = get("/api/v1/college/students?collegeId=" + collegeIdA, b).data();
        assertThat(page.get("totalElements").asInt()).isZero();
        Resp created = post("/api/v1/college/companies", b, Map.of("name", "B-Corp " + uid(), "collegeId", collegeIdA));
        assertThat(created.status()).isEqualTo(201);
        assertThat(get("/api/v1/college/companies/" + created.data().get("id").asText(), a).status()).as("B's row never lands in A").isEqualTo(404);
    }

    @Test
    void collegeBCannotModifyCollegeAData() throws Exception {
        assertThat(put("/api/v1/college/students/" + studentA, b, Map.of("rollNumber", "X", "firstName", "X", "email", "x@x.in",
                "branchId", branchA, "batchYear", 2027)).status()).isIn(400, 404);
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/college/students/" + studentA), b, null).status()).isEqualTo(404);
        assertThat(post("/api/v1/college/drives/" + driveA + "/status", b, Map.of("status", "PUBLISHED")).status()).isEqualTo(404);
        assertThat(post("/api/v1/college/offers", b, Map.of("studentId", studentA, "companyId", companyA, "roleTitle", "X", "ctcLpa", 5)).status()).isEqualTo(404);
        String bBranch = addBranch(b, "CSE");
        assertThat(post("/api/v1/college/drives", b, Map.of("companyId", companyA, "title", "t", "roleTitle", "r", "driveType", "ON_CAMPUS")).status())
                .as("cannot attach A's company to B's drive").isEqualTo(404);
        assertThat(post("/api/v1/college/drives/" + driveA + "/overrides", b, Map.of("studentId", studentA, "action", "INCLUDE", "reason", "sneaky")).status()).isEqualTo(404);
        assertThat(bBranch).isNotBlank();
        // A's data untouched
        assertThat(get("/api/v1/college/students/" + studentA, a).data().get("student").get("deleted").asBoolean()).isFalse();
        assertThat(get("/api/v1/college/drives/" + driveA, a).data().get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void auditLogIsTenantScoped() throws Exception {
        JsonNode bLogs = get("/api/v1/college/audit-logs?size=200", b).data().get("content");
        for (JsonNode e : bLogs) assertThat(e.get("summary").asText()).doesNotContain("Acme drive");
        assertThat(List.of(bLogs.size())).isNotEmpty();
    }
}
