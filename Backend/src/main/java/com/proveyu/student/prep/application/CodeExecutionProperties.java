package com.proveyu.student.prep.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Code execution settings, all from configuration/env (see application.yml and Backend/.env.example).
 * Moving from the local Judge0 (SSH tunnel) to the VM needs only env changes: JUDGE0_URL, JUDGE0_TOKEN, ...
 */
@ConfigurationProperties("code.execution")
public record CodeExecutionProperties(
        @DefaultValue("judge0") String provider,
        /** Minimum seconds between two Run clicks (and two Submit clicks) by the same student. 0 = no limit. */
        @DefaultValue("5") int rateLimitSeconds,
        /** How long a run waits in the backend queue for a free Judge0 slot before "judge busy". */
        @DefaultValue("20000") long queueWaitMs,
        /** Max size of the program source (bytes of UTF-8 are checked, not characters). */
        @DefaultValue("65536") int maxSourceBytes,
        /** Max size of one stdin. Admin test cases can be large (some hidden inputs are ~200 KB); Judge0 accepts 1 MB. */
        @DefaultValue("1048576") int maxStdinBytes,
        /** Output kept per run; longer output is cut so the page stays small. */
        @DefaultValue("65536") int maxOutputBytes,
        @DefaultValue Judge0 judge0,
        /** Language key (as sent by the frontend) to Judge0 language_id. Set in application.yml; verify the ids with GET /languages on your Judge0. */
        Map<String, Integer> languages
) {

    public static final Map<String, Integer> DEFAULT_LANGUAGES = Map.of(
            "python", 71, "c", 50, "cpp", 54, "java", 62, "javascript", 63);

    public CodeExecutionProperties {
        if (provider == null || provider.isBlank()) provider = "judge0";
        if (languages == null || languages.isEmpty()) languages = DEFAULT_LANGUAGES;
        if (judge0 == null) judge0 = new Judge0(null, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); // zeros/nulls = defaults applied by the Judge0 constructor
    }

    public record Judge0(
            @DefaultValue("http://localhost:2358") String url,
            /** Sent in the header named by authHeader. Empty = no header (only for a Judge0 without AUTHN_TOKEN). Never logged. */
            @DefaultValue("") String token,
            @DefaultValue("X-Auth-Token") String authHeader,
            @DefaultValue("300") long pollIntervalMs,
            @DefaultValue("30000") long pollTimeoutMs,
            @DefaultValue("4") int maxParallel,
            @DefaultValue("3000") int connectTimeoutMs,
            @DefaultValue("15000") int readTimeoutMs,
            /** Default CPU seconds per test (the old runner allowed 3 s). */
            @DefaultValue("3") double cpuTimeLimitSeconds,
            /** Default wall-clock seconds per test. */
            @DefaultValue("8") double wallTimeLimitSeconds,
            /** Default memory per test in KB (256 MB). Java may need more: check with a live run. */
            @DefaultValue("262144") long memoryLimitKb,
            /** Hard caps: a value above these is clamped, so config mistakes cannot give students unlimited resources. */
            @DefaultValue("10") double maxCpuTimeLimitSeconds,
            @DefaultValue("15") double maxWallTimeLimitSeconds,
            @DefaultValue("524288") long maxMemoryLimitKb,
            /** Judge0 accepts at most 20 submissions per batch by default; larger lists are split. */
            @DefaultValue("20") int maxBatchSize
    ) {
        public Judge0 {
            if (url == null || url.isBlank()) url = "http://localhost:2358";
            if (token == null) token = "";
            if (authHeader == null || authHeader.isBlank()) authHeader = "X-Auth-Token";
            if (pollIntervalMs <= 0) pollIntervalMs = 300;
            if (pollTimeoutMs <= 0) pollTimeoutMs = 30_000;
            if (maxParallel <= 0) maxParallel = 4;
            if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
            if (readTimeoutMs <= 0) readTimeoutMs = 15_000;
            if (cpuTimeLimitSeconds <= 0) cpuTimeLimitSeconds = 3;
            if (wallTimeLimitSeconds <= 0) wallTimeLimitSeconds = 8;
            if (memoryLimitKb <= 0) memoryLimitKb = 262_144;
            if (maxCpuTimeLimitSeconds <= 0) maxCpuTimeLimitSeconds = 10;
            if (maxWallTimeLimitSeconds <= 0) maxWallTimeLimitSeconds = 15;
            if (maxMemoryLimitKb <= 0) maxMemoryLimitKb = 524_288;
            if (maxBatchSize <= 0) maxBatchSize = 20;
            url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        /** Per-test limits: the configured default, clamped to the hard cap. */
        public double cpuLimit() { return Math.min(cpuTimeLimitSeconds, maxCpuTimeLimitSeconds); }
        public double wallLimit() { return Math.min(wallTimeLimitSeconds, maxWallTimeLimitSeconds); }
        public long memoryLimit() { return Math.min(memoryLimitKb, maxMemoryLimitKb); }
    }
}
