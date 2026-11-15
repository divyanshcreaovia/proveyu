package com.proveyu.college.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** With the dev flag off (production default) no OTP / verification / invite token ever appears in a response. */
@TestPropertySource(properties = "app.dev.expose-tokens=false")
class ProductionTokenExposureIT extends IntegrationTestBase {

    @Test
    void tokensAreNeverReturned() throws Exception {
        String email = uid() + "@cand.in";
        post("/api/v1/auth/register", null, Map.of("email", email, "password", "Secret123", "fullName", "C", "role", "CANDIDATE"));
        Resp forgot = post("/api/v1/auth/forgot-password", null, Map.of("email", email));
        assertThat(forgot.status()).isEqualTo(200);
        assertThat(forgot.data().has("devOtp")).isFalse();
        assertThat(forgot.body().toString()).doesNotContainPattern("\\b\\d{6}\\b");

        String domain = "p" + uid() + ".edu";
        Resp reg = post("/api/v1/college/auth/register", null, Map.ofEntries(Map.entry("collegeName", "P College"), Map.entry("aicteCode", "P-" + uid()),
                Map.entry("university", "U"), Map.entry("city", "C"), Map.entry("state", "S"), Map.entry("emailDomain", domain), Map.entry("tpoName", "T"),
                Map.entry("tpoPhone", "6" + String.valueOf(System.nanoTime()).substring(4, 13)), Map.entry("tpoEmail", "tpo@" + domain),
                Map.entry("password", "Secret123"), Map.entry("acceptTerms", true)));
        assertThat(reg.status()).isEqualTo(201);
        assertThat(reg.data().has("devVerificationToken")).isFalse();
    }
}
