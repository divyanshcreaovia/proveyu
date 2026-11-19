package com.proveyu.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    /** Over the sign-in limit the caller gets a real 429 with a JSON body (it used to fail writing the body and became a 500 / 401). */
    @Test
    void overTheLimitAnswers429WithAJsonBody() throws Exception {
        RateLimitingFilter f = new RateLimitingFilter(null);
        ReflectionTestUtils.setField(f, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(f, "authLimitPerMinute", 3);
        ReflectionTestUtils.setField(f, "userLimitPerMinute", 120);
        MockHttpServletResponse last = null;
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.7");
            last = new MockHttpServletResponse();
            f.doFilter(req, last, new MockFilterChain());
            if (i < 3) assertThat(last.getStatus()).isEqualTo(200);
        }
        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getContentAsString()).contains("\"success\":false").contains("RATE_LIMIT_EXCEEDED").contains("\"timestamp\":\"20");
    }

    /** A fake X-Forwarded-For per request must not give an attacker a fresh rate-limit bucket. */
    @Test
    void aFakeForwardedForDoesNotDodgeTheLimit() throws Exception {
        RateLimitingFilter f = new RateLimitingFilter(null);
        ReflectionTestUtils.setField(f, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(f, "authLimitPerMinute", 3);
        ReflectionTestUtils.setField(f, "userLimitPerMinute", 120);
        int last = 0;
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("198.51.100.9");
            req.addHeader("X-Forwarded-For", "203.0.113." + i);
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, new MockFilterChain());
            last = res.getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void behindTrustedNginxTheRealClientIsTheRightMostAddress() {
        RateLimitingFilter f = new RateLimitingFilter(null);
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.9");
        direct.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(f.getClientIp(direct)).isEqualTo("198.51.100.9"); // no trusted proxy: the header is ignored

        ReflectionTestUtils.setField(f, "trustedProxies", java.util.List.of("127.0.0.1"));
        MockHttpServletRequest viaNginx = new MockHttpServletRequest();
        viaNginx.setRemoteAddr("127.0.0.1");
        viaNginx.addHeader("X-Forwarded-For", "6.6.6.6, 49.36.10.20"); // client wrote 6.6.6.6, nginx appended the real address
        assertThat(f.getClientIp(viaNginx)).isEqualTo("49.36.10.20");
    }
}
