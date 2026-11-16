package com.proveyu.college.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;
import java.util.UUID;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Boots the full application against a real MySQL 8 (Testcontainers) with every Flyway migration applied.
 * One container is shared by all integration test classes (singleton pattern).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("PROVEYU").withUsername("test").withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected record Resp(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }

    protected Resp call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        MvcResult r = mvc.perform(req).andReturn();
        String s = r.getResponse().getContentAsString();
        return new Resp(r.getResponse().getStatus(), s.isEmpty() ? json.createObjectNode() : json.readTree(s));
    }

    protected Resp get(String url, String token) throws Exception { return call(MockMvcRequestBuilders.get(url), token, null); }
    protected Resp post(String url, String token, Object body) throws Exception { return call(MockMvcRequestBuilders.post(url), token, body); }
    protected Resp put(String url, String token, Object body) throws Exception { return call(MockMvcRequestBuilders.put(url), token, body); }
    protected Resp patch(String url, String token, Object body) throws Exception { return call(MockMvcRequestBuilders.patch(url), token, body); }

    protected static String uid() { return UUID.randomUUID().toString().substring(0, 8); }

    /** Registers + verifies a college and returns the TPO token. */
    protected String registerCollege(String tag) throws Exception {
        String domain = tag + uid() + ".edu.in";
        String email = "tpo@" + domain;
        Resp reg = post("/api/v1/college/auth/register", null, Map.ofEntries(
                Map.entry("collegeName", "College " + tag + " " + uid()), Map.entry("aicteCode", "AICTE-" + uid()), Map.entry("university", "MU"),
                Map.entry("city", "Mumbai"), Map.entry("state", "Maharashtra"), Map.entry("emailDomain", domain), Map.entry("tpoName", "TPO " + tag),
                Map.entry("tpoPhone", "9" + String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L))), Map.entry("tpoEmail", email),
                Map.entry("password", "Secret123"), Map.entry("acceptTerms", true)));
        if (reg.status() != 201) throw new IllegalStateException("register failed: " + reg.body());
        post("/api/v1/auth/verify-email", null, Map.of("token", reg.data().get("devVerificationToken").asText()));
        return login(email, "Secret123");
    }

    protected String login(String email, String password) throws Exception {
        Resp r = post("/api/v1/auth/login", null, Map.of("email", email, "password", password));
        if (r.status() != 200) throw new IllegalStateException("login failed: " + r.body());
        return r.data().get("token").asText();
    }

    protected String addBranch(String token, String code) throws Exception {
        return post("/api/v1/college/settings/branches", token, Map.of("code", code, "name", code + " branch")).data().get("id").asText();
    }

    protected JsonNode addStudent(String token, String branchId, String roll, double cgpa, int backlogs) throws Exception {
        Resp r = post("/api/v1/college/students", token, Map.of("rollNumber", roll, "firstName", "Stu" + roll, "email", roll.toLowerCase() + "@stu.in",
                "branchId", branchId, "batchYear", 2027, "cgpa", cgpa, "activeBacklogs", backlogs));
        if (r.status() != 201) throw new IllegalStateException("student failed: " + r.body());
        return r.data();
    }
}
