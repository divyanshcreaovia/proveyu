package com.proveyu.student.prep.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Reports whether the code runner can be used. Calls Judge0 GET /languages (the judge's own check of its auth and health).
 * Used by the admin endpoint and once at startup (a warning in the log, the backend still starts).
 */
@Slf4j
@Service
public class CodeRunnerHealthService {

    public record Report(String provider, boolean reachable, String baseUrl, Integer languages, String detail) {}

    private final CodeExecutionProperties props;
    private final Judge0Client client;

    public CodeRunnerHealthService(CodeExecutionProperties props, Judge0Client client) {
        this.props = props;
        this.client = client;
    }

    public Report check() {
        String provider = props.provider();
        if (!"judge0".equalsIgnoreCase(provider)) {
            return new Report(provider, true, null, null, "Using the legacy Docker runner; Judge0 is not checked.");
        }
        try {
            int n = client.languageCount();
            return new Report(provider, n > 0, client.baseUrl(), n, n > 0 ? "Judge0 reachable and authorised." : "Judge0 answered but lists no languages.");
        } catch (CodeRunnerUnavailableException e) {
            return new Report(provider, false, client.baseUrl(), null, e.getReason().name() + ": " + e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAtStartup() {
        Report r = check();
        if (r.reachable()) {
            log.info("[CODE RUNNER] provider={} ready ({} languages at {})", r.provider(), r.languages(), r.baseUrl());
        } else {
            log.warn("[CODE RUNNER] provider={} NOT reachable: {}. Coding questions will show 'not available' until it is fixed.", r.provider(), r.detail());
        }
    }
}
