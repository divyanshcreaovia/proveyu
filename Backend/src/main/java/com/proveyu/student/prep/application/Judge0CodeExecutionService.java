package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import com.proveyu.student.prep.application.Judge0Client.Judgement;
import com.proveyu.student.prep.application.Judge0Client.Submission;
import com.proveyu.student.prep.domain.PrepModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Default code runner: sends each run to Judge0 CE (isolate sandbox on a separate VM). Judge0 only compiles and runs;
 * the output is compared here in Java, as the Docker runner did. A bounded semaphore keeps a spike of students from
 * overloading Judge0: extra runs wait in this queue, and "judge busy" is returned if the wait is too long.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "code.execution.provider", havingValue = "judge0", matchIfMissing = true)
public class Judge0CodeExecutionService implements CodeExecutionService {

    private static final int MAX_COMPILE_CHARS = 4000;
    private static final int MAX_STDERR_BYTES = 4000;

    private final Judge0Client client;
    private final CodeExecutionProperties props;
    private final Semaphore slots;

    public Judge0CodeExecutionService(Judge0Client client, CodeExecutionProperties props) {
        this.client = client;
        this.props = props;
        this.slots = new Semaphore(props.judge0().maxParallel(), true);
    }

    @Override
    public Result run(String language, String code, List<String> inputs) {
        int languageId = languageId(language);
        checkSource(code);
        List<String> stdins = inputs == null ? List.of() : inputs;
        for (String in : stdins) {
            if (in != null && in.getBytes(StandardCharsets.UTF_8).length > props.maxStdinBytes()) {
                throw new DomainException("A test input is too large", HttpStatus.BAD_REQUEST, "INPUT_TOO_LARGE");
            }
        }
        if (stdins.isEmpty()) return new Result(null, List.of());

        acquireSlot();
        try {
            CodeExecutionProperties.Judge0 cfg = props.judge0();
            List<Submission> subs = new ArrayList<>(stdins.size());
            for (String in : stdins) {
                subs.add(new Submission(code, in == null ? "" : in, languageId, cfg.cpuLimit(), cfg.wallLimit(), cfg.memoryLimit()));
            }
            List<String> tokens = client.createBatch(subs);
            return toResult(client.awaitBatch(tokens));
        } finally {
            slots.release();
        }
    }

    private Result toResult(List<Judgement> js) {
        for (Judgement j : js) {
            if (j.verdict() == Judge0Verdict.COMPILATION_ERROR) {
                String msg = truncate(j.compileOutput(), MAX_COMPILE_CHARS);
                return new Result(msg.isBlank() ? "Compilation failed" : msg, List.of());
            }
        }
        for (Judgement j : js) {
            Judge0Verdict v = j.verdict();
            if (v == Judge0Verdict.INTERNAL_ERROR || v == Judge0Verdict.EXEC_FORMAT_ERROR || v == Judge0Verdict.UNKNOWN
                    || v == Judge0Verdict.IN_QUEUE || v == Judge0Verdict.PROCESSING) {
                // Not the student's fault: the judge (or its configuration) failed. Logged with ids only, never the source.
                log.error("[JUDGE0] system verdict {} (status {} '{}'), judge message: {}", v, j.statusId(), j.statusDescription(),
                        truncate(j.message(), 200).replace('\n', ' '));
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.INTERNAL);
            }
        }
        List<Output> outs = new ArrayList<>(js.size());
        for (Judgement j : js) outs.add(output(j));
        return new Result(null, outs);
    }

    private Output output(Judgement j) {
        String stdout = truncateBytes(j.stdout(), props.maxOutputBytes());
        return switch (j.verdict()) {
            case TIME_LIMIT_EXCEEDED -> new Output(stdout, truncateBytes(j.stderr(), MAX_STDERR_BYTES), 124, true);
            case RUNTIME_ERROR -> {
                String err = j.stderr().isBlank() ? j.statusDescription() : j.stderr();
                yield new Output(stdout, truncateBytes(err, MAX_STDERR_BYTES), 1, false);
            }
            default -> new Output(stdout, truncateBytes(j.stderr(), MAX_STDERR_BYTES), 0, false); // ACCEPTED, WRONG_ANSWER
        };
    }

    private int languageId(String language) {
        if (language == null || language.isBlank()) throw new DomainException("Choose a language", HttpStatus.BAD_REQUEST, "LANGUAGE_REQUIRED");
        String key = language.toLowerCase(Locale.ROOT);
        Integer id = PrepModel.LANGUAGES.contains(key) ? props.languages().get(key) : null;
        if (id == null) throw new DomainException("Unsupported language: " + language, HttpStatus.BAD_REQUEST, "LANGUAGE_UNSUPPORTED");
        return id;
    }

    private void checkSource(String code) {
        if (code == null || code.isBlank()) throw new DomainException("Write some code first", HttpStatus.BAD_REQUEST, "EMPTY_CODE");
        if (code.getBytes(StandardCharsets.UTF_8).length > props.maxSourceBytes()) {
            throw new DomainException("Code is too long (max " + props.maxSourceBytes() / 1024 + " KB)", HttpStatus.BAD_REQUEST, "CODE_TOO_LONG");
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(props.queueWaitMs(), TimeUnit.MILLISECONDS)) {
                log.warn("[JUDGE0] queue full: no free slot in {} ms (max parallel {})", props.queueWaitMs(), props.judge0().maxParallel());
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.BUSY);
        }
    }

    static String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    static String truncateBytes(String s, int maxBytes) {
        if (s == null) return "";
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return b.length <= maxBytes ? s : new String(b, 0, maxBytes, StandardCharsets.UTF_8);
    }
}
