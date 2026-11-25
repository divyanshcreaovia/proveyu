package com.proveyu.student.prep.application;

import java.util.List;

/**
 * Runs a student's program against a list of stdin inputs (the visible samples for Run, the hidden tests for Submit).
 * Implementations: {@link Judge0CodeExecutionService} (default) and {@link DockerCodeRunner} (legacy, {@code code.execution.provider=docker}).
 * Both return the same shape, so scoring and the API responses do not depend on the provider.
 */
public interface CodeExecutionService {

    /** One run of the program against one stdin. timedOut = the time limit was hit. */
    record Output(String stdout, String stderr, int exitCode, boolean timedOut) {}

    /** compileError set = the program did not compile (outputs empty). */
    record Result(String compileError, List<Output> outputs) {}

    /** Runs the program once per input, in order. The caller decides which inputs (samples or hidden tests). */
    Result run(String language, String code, List<String> inputs);

    /**
     * Output comparison that ignores trailing spaces and trailing blank lines (what students expect from online judges).
     * Comparison is done here, in Java, for every provider, so a verdict never depends on the judge's own rules.
     */
    static boolean sameOutput(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").lines().map(String::stripTrailing).reduce((a, b) -> a + "\n" + b).orElse("").stripTrailing();
    }
}
