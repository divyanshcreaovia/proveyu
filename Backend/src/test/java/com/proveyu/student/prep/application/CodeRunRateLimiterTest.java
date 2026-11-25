package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeRunRateLimiterTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    void secondRunInsideTheIntervalGets429WithTheWaitTime() {
        CodeRunRateLimiter limiter = new CodeRunRateLimiter(5);
        UUID student = UUID.randomUUID();
        limiter.check(student, CodeRunRateLimiter.Action.RUN, 0);

        assertThatThrownBy(() -> limiter.check(student, CodeRunRateLimiter.Action.RUN, SEC))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getErrorCode()).isEqualTo("RATE_LIMITED");
                    assertThat(e.getMessage()).isEqualTo("You are running code too fast. Wait 4 seconds and try again.");
                });
    }

    @Test
    void allowedAgainOnceTheIntervalHasPassed() {
        CodeRunRateLimiter limiter = new CodeRunRateLimiter(5);
        UUID student = UUID.randomUUID();
        limiter.check(student, CodeRunRateLimiter.Action.RUN, 0);
        assertThatThrownBy(() -> limiter.check(student, CodeRunRateLimiter.Action.RUN, 2 * SEC)).isInstanceOf(DomainException.class);

        limiter.check(student, CodeRunRateLimiter.Action.RUN, 5 * SEC); // no exception
    }

    @Test
    void runAndSubmitHaveSeparateBuckets() {
        CodeRunRateLimiter limiter = new CodeRunRateLimiter(5);
        UUID student = UUID.randomUUID();
        limiter.check(student, CodeRunRateLimiter.Action.RUN, 0);
        limiter.check(student, CodeRunRateLimiter.Action.SUBMIT, SEC); // a Run just before does not block Submit
    }

    @Test
    void studentsAreLimitedSeparately() {
        CodeRunRateLimiter limiter = new CodeRunRateLimiter(5);
        limiter.check(UUID.randomUUID(), CodeRunRateLimiter.Action.RUN, 0);
        limiter.check(UUID.randomUUID(), CodeRunRateLimiter.Action.RUN, 0);
    }

    @Test
    void zeroDisablesTheLimit() {
        CodeRunRateLimiter limiter = new CodeRunRateLimiter(0);
        UUID student = UUID.randomUUID();
        for (int i = 0; i < 20; i++) limiter.check(student, CodeRunRateLimiter.Action.RUN);
    }
}
