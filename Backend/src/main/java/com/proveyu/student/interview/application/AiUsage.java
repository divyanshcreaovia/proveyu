package com.proveyu.student.interview.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI usage since the server started, for the admin dashboard: cloud (Groq) calls and tokens, calls that fell back to the local
 * model, and speech-to-text. In memory only; it starts from zero after a restart.
 */
public final class AiUsage {
    private AiUsage() {}

    public record Snapshot(Instant since, long cloudCalls, long cloudTokens, long cloudFailures, long localCalls, long cloudTranscriptions,
                           long localTranscriptions) {}

    private static final Instant SINCE = Instant.now();
    private static final AtomicLong CLOUD_CALLS = new AtomicLong(), CLOUD_TOKENS = new AtomicLong(), CLOUD_FAILURES = new AtomicLong(),
            LOCAL_CALLS = new AtomicLong(), CLOUD_STT = new AtomicLong(), LOCAL_STT = new AtomicLong();

    static void cloudCall(long tokens) { CLOUD_CALLS.incrementAndGet(); CLOUD_TOKENS.addAndGet(Math.max(0, tokens)); }
    static void cloudFailure() { CLOUD_FAILURES.incrementAndGet(); }
    static void localCall() { LOCAL_CALLS.incrementAndGet(); }
    static void cloudTranscription() { CLOUD_STT.incrementAndGet(); }
    static void localTranscription() { LOCAL_STT.incrementAndGet(); }

    public static Snapshot snapshot() {
        return new Snapshot(SINCE, CLOUD_CALLS.get(), CLOUD_TOKENS.get(), CLOUD_FAILURES.get(), LOCAL_CALLS.get(), CLOUD_STT.get(), LOCAL_STT.get());
    }
}
