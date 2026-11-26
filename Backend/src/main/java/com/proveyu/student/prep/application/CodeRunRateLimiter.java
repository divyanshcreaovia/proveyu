package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-student minimum interval between two code runs of the same kind (Run, or Submit). In memory, per backend instance:
 * enough to stop one student from flooding the judge; a multi-instance deployment would need a shared store.
 */
@Component
public class CodeRunRateLimiter {

    /** Run and Submit have separate buckets: Submit right after a Run is allowed. */
    public enum Action { RUN, SUBMIT, PLAYGROUND, CODE_GROUND, CODE_GROUND_SUBMIT }

    private static final int PRUNE_AT = 10_000;

    private final long minIntervalNanos;
    private final Map<String, Long> last = new ConcurrentHashMap<>();

    public CodeRunRateLimiter(@Value("${code.execution.rate-limit-seconds:5}") int rateLimitSeconds) {
        this.minIntervalNanos = Math.max(0, rateLimitSeconds) * 1_000_000_000L;
    }

    /** Lets the call through, or throws a 429 with the seconds to wait. */
    public void check(UUID userId, Action action) {
        if (minIntervalNanos <= 0) return;
        check(userId, action, System.nanoTime());
    }

    /** Clock-injectable version (used by the tests). */
    void check(UUID userId, Action action, long nowNanos) {
        if (minIntervalNanos <= 0) return;
        String key = action + ":" + userId;
        long[] waitNanos = {0};
        last.compute(key, (k, prev) -> {
            if (prev != null && nowNanos - prev < minIntervalNanos) {
                waitNanos[0] = minIntervalNanos - (nowNanos - prev);
                return prev;
            }
            return nowNanos;
        });
        if (last.size() > PRUNE_AT) prune(nowNanos);
        if (waitNanos[0] > 0) {
            long seconds = (waitNanos[0] + 999_999_999L) / 1_000_000_000L;
            throw new DomainException("You are running code too fast. Wait " + seconds + " second" + (seconds == 1 ? "" : "s") + " and try again.",
                    HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
    }

    private void prune(long nowNanos) {
        last.entrySet().removeIf(e -> nowNanos - e.getValue() >= minIntervalNanos);
    }
}
