package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs student code in a throwaway Docker container: no network, read-only root, 256 MB RAM, 1 CPU, 64 processes,
 * unprivileged user, a per-test time limit and capped output. One container compiles once and runs every input.
 * shortcut: docker-per-run on the app host is fine for pilots; move to a dedicated Judge0 / gVisor pool before large drives.
 */
@Slf4j
@Component
public class DockerCodeRunner {

    public record Output(String stdout, String stderr, int exitCode, boolean timedOut) {}

    public record Result(String compileError, List<Output> outputs) {}

    private static final int MAX_OUTPUT_BYTES = 65_536;

    private final String image;
    private final int perTestSeconds;
    private final Semaphore slots;

    public DockerCodeRunner(@Value("${prep.runner.image:proveyu-code-runner:1}") String image,
                            @Value("${prep.runner.per-test-seconds:3}") int perTestSeconds,
                            @Value("${prep.runner.max-parallel:4}") int maxParallel) {
        this.image = image;
        this.perTestSeconds = perTestSeconds;
        this.slots = new Semaphore(maxParallel, true);
    }

    public Result run(String language, String code, List<String> inputs) {
        Lang lang = Lang.of(language);
        if (code == null || code.isBlank()) throw new DomainException("Write some code first", HttpStatus.BAD_REQUEST, "EMPTY_CODE");
        if (code.length() > 64_000) throw new DomainException("Code is too long (max 64 KB)", HttpStatus.BAD_REQUEST, "CODE_TOO_LONG");
        boolean acquired = false;
        Path dir = null;
        String name = "prep-run-" + UUID.randomUUID();
        try {
            acquired = slots.tryAcquire(20, TimeUnit.SECONDS);
            if (!acquired) throw new DomainException("The code runner is busy. Try again in a few seconds.", HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_BUSY");
            dir = Files.createTempDirectory("prep-run-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")));
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx")); // umask may strip bits; the container user must write here
            Files.writeString(dir.resolve(lang.file), code, StandardCharsets.UTF_8);
            for (int i = 0; i < inputs.size(); i++) Files.writeString(dir.resolve("in" + (i + 1)), inputs.get(i) == null ? "" : inputs.get(i));
            Files.writeString(dir.resolve("run.sh"), script(lang, inputs.size()));

            List<String> cmd = List.of("docker", "run", "--rm", "--name", name, "--network", "none", "--read-only",
                    "--tmpfs", "/tmp:rw,exec,size=64m", "--memory", "256m", "--memory-swap", "256m", "--cpus", "1", "--pids-limit", "64",
                    "--user", "65534:65534", "--security-opt", "no-new-privileges", "-v", dir + ":/code", image, "bash", "/code/run.sh");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            long wallSeconds = 25L + (long) inputs.size() * (perTestSeconds + 2);
            if (!p.waitFor(wallSeconds, TimeUnit.SECONDS)) {
                new ProcessBuilder("docker", "kill", name).start().waitFor(5, TimeUnit.SECONDS);
                p.destroyForcibly();
                throw new DomainException("Your program took too long to finish", HttpStatus.UNPROCESSABLE_ENTITY, "RUN_TIMEOUT");
            }
            String compileError = read(dir.resolve("compile.err"));
            if (Files.exists(dir.resolve("compile.failed"))) return new Result(compileError.isBlank() ? "Compilation failed" : compileError, List.of());
            List<Output> outs = new ArrayList<>();
            for (int i = 1; i <= inputs.size(); i++) {
                String rc = read(dir.resolve("rc" + i)).trim();
                int code1 = rc.isEmpty() ? -1 : Integer.parseInt(rc);
                outs.add(new Output(read(dir.resolve("out" + i)), read(dir.resolve("err" + i)), code1, code1 == 137 || code1 == 124));
            }
            return new Result(null, outs);
        } catch (IOException e) {
            log.error("[PREP RUNNER] docker run failed", e);
            throw new DomainException("The code runner is not available right now", HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_UNAVAILABLE");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("Run interrupted", HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_UNAVAILABLE");
        } finally {
            if (acquired) slots.release();
            if (dir != null) deleteQuietly(dir);
        }
    }

    private String script(Lang lang, int n) {
        StringBuilder s = new StringBuilder("#!/bin/bash\ncd /tmp\n");
        if (lang.compile != null) s.append(lang.compile).append(" 2> /code/compile.err || { touch /code/compile.failed; exit 0; }\n");
        s.append("for i in $(seq 1 ").append(n).append("); do\n")
                .append("  timeout -s KILL ").append(perTestSeconds).append(' ').append(lang.run)
                .append(" < /code/in$i 2> >(head -c 4000 > /code/err$i) | head -c ").append(MAX_OUTPUT_BYTES).append(" > /code/out$i\n")
                .append("  echo ${PIPESTATUS[0]} > /code/rc$i\n")
                .append("done\n");
        return s.toString();
    }

    private static String read(Path f) throws IOException {
        return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> w = Files.walk(dir)) {
            w.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException ignored) {
            // temp dir; the OS cleans /tmp eventually
        }
    }

    /** Output comparison that ignores trailing spaces and trailing blank lines (what students expect from online judges). */
    public static boolean sameOutput(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").lines().map(String::stripTrailing).reduce((a, b) -> a + "\n" + b).orElse("").stripTrailing();
    }

    enum Lang {
        PYTHON("main.py", null, "python3 /code/main.py"),
        JAVASCRIPT("main.js", null, "node /code/main.js"),
        JAVA("Main.java", "javac -d /tmp/b /code/Main.java", "java -Xmx128m -Xss16m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp /tmp/b Main"),
        CPP("main.cpp", "g++ -O2 -std=c++17 -o /tmp/a /code/main.cpp", "/tmp/a");

        final String file;
        final String compile;
        final String run;

        Lang(String file, String compile, String run) {
            this.file = file;
            this.compile = compile;
            this.run = run;
        }

        static Lang of(String language) {
            if (language == null) throw new DomainException("Choose a language", HttpStatus.BAD_REQUEST, "LANGUAGE_REQUIRED");
            return switch (language.toLowerCase()) {
                case "python" -> PYTHON;
                case "javascript" -> JAVASCRIPT;
                case "java" -> JAVA;
                case "cpp" -> CPP;
                default -> throw new DomainException("Unsupported language: " + language, HttpStatus.BAD_REQUEST, "LANGUAGE_UNSUPPORTED");
            };
        }
    }
}
