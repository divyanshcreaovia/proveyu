package com.proveyu.student.prep.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin HTTP client for the Judge0 CE API. Base64 on every text field (so stdin/stdout with any bytes survive), one batch
 * create per chunk of submissions, and a poll of the batch until every status is final. Never uses {@code wait=true}.
 * The auth token is sent only in its header; it is never logged, and neither is source code.
 *
 * <p>Uses the JDK HttpClient directly (HTTP/1.1, body sent with a Content-Length). Spring's RestClient and HttpURLConnection
 * send POST bodies chunked, and Judge0 then receives an empty body and answers 400.
 */
@Slf4j
@Component
@EnableConfigurationProperties(CodeExecutionProperties.class)
public class Judge0Client {

    /** Fields requested on every poll; keeps the response small. */
    static final String POLL_FIELDS = "token,status,stdout,stderr,compile_output,message";

    /** One program run: source plus one stdin plus limits. */
    public record Submission(String sourceCode, String stdin, int languageId, double cpuTimeLimit, double wallTimeLimit, long memoryLimitKb) {}

    /** A finished run as Judge0 reported it, with text fields already decoded from base64. */
    public record Judgement(String token, int statusId, String statusDescription, String stdout, String stderr, String compileOutput, String message) {
        public Judge0Verdict verdict() {
            return Judge0Verdict.of(statusId);
        }
    }

    private final CodeExecutionProperties.Judge0 cfg;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public Judge0Client(CodeExecutionProperties properties) {
        this.cfg = properties.judge0();
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .build();
    }

    /** The Judge0 base URL in use (for logs and the health report; no secrets). */
    public String baseUrl() {
        return cfg.url();
    }

    /** Creates the submissions in batches of at most {@code maxBatchSize}; returns one token per submission, in order. */
    public List<String> createBatch(List<Submission> submissions) {
        List<String> tokens = new ArrayList<>(submissions.size());
        for (int from = 0; from < submissions.size(); from += cfg.maxBatchSize()) {
            List<Submission> chunk = submissions.subList(from, Math.min(from + cfg.maxBatchSize(), submissions.size()));
            List<Map<String, Object>> body = new ArrayList<>(chunk.size());
            for (Submission s : chunk) {
                Map<String, Object> m = new HashMap<>();
                m.put("language_id", s.languageId());
                m.put("source_code", b64(s.sourceCode()));
                m.put("stdin", b64(s.stdin()));
                m.put("cpu_time_limit", s.cpuTimeLimit());
                m.put("wall_time_limit", s.wallTimeLimit());
                m.put("memory_limit", s.memoryLimitKb());
                body.add(m);
            }
            JsonNode res = call("create batch", "POST", "/submissions/batch?base64_encoded=true",
                    toJson(Map.of("submissions", body)));
            if (res == null || !res.isArray() || res.size() != chunk.size()) {
                log.error("[JUDGE0] create batch returned an unexpected reply (expected {} tokens)", chunk.size());
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.INTERNAL);
            }
            for (JsonNode t : res) tokens.add(t.path("token").asText());
        }
        return tokens;
    }

    /**
     * Polls the batch until every submission is final (status id above 2) or the poll timeout passes.
     * Returns the judgements in the same order as the tokens.
     */
    public List<Judgement> awaitBatch(List<String> tokens) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cfg.pollTimeoutMs());
        String path = "/submissions/batch?tokens=" + enc(String.join(",", tokens))
                + "&base64_encoded=true&fields=" + enc(POLL_FIELDS);
        while (true) {
            JsonNode res = call("poll batch", "GET", path, null);
            Map<String, Judgement> byToken = new HashMap<>();
            JsonNode subs = res == null ? null : res.path("submissions");
            if (subs != null && subs.isArray()) for (JsonNode s : subs) {
                Judgement j = judgement(s);
                byToken.put(j.token(), j);
            }
            List<Judgement> out = new ArrayList<>(tokens.size());
            boolean done = true;
            for (String t : tokens) {
                Judgement j = byToken.get(t);
                if (j == null || !Judge0Verdict.isFinished(j.statusId())) { done = false; break; }
                out.add(j);
            }
            if (done) return out;
            if (System.nanoTime() > deadline) {
                log.error("[JUDGE0] poll timed out after {} ms ({} submissions still running)", cfg.pollTimeoutMs(), tokens.size());
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.TIMEOUT);
            }
            sleep(cfg.pollIntervalMs());
        }
    }

    /** Number of languages the judge knows (GET /languages). Used by the health check; throws CodeRunnerUnavailableException when it cannot answer. */
    public int languageCount() {
        JsonNode res = call("languages", "GET", "/languages", null);
        return res == null || !res.isArray() ? 0 : res.size();
    }

    private Judgement judgement(JsonNode s) {
        int id = s.path("status").path("id").asInt(s.path("status_id").asInt(0));
        return new Judgement(s.path("token").asText(), id, s.path("status").path("description").asText(""),
                decode(s.path("stdout")), decode(s.path("stderr")), decode(s.path("compile_output")), decode(s.path("message")));
    }

    private static String decode(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return "";
        try {
            // Judge0 ends its base64 with a newline ("MQo=\n"), which the strict decoder rejects: remove whitespace first
            return new String(Base64.getDecoder().decode(n.asText().replaceAll("\\s", "")), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("[JUDGE0] could not decode a text field from the judge; treating it as empty");
            return "";
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalStateException("cannot encode the Judge0 request", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.TIMEOUT);
        }
    }

    /**
     * Sends one request and turns every failure into a student-safe exception plus a developer log line.
     * 401/403 = bad token (AUTH); other HTTP errors = INTERNAL; read/connect timeouts = TIMEOUT; no connection = UNREACHABLE.
     * Only the path is logged: the token is in a header and the source code is in the body, neither is logged.
     */
    private JsonNode call(String what, String method, String pathAndQuery, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.url() + pathAndQuery))
                .timeout(Duration.ofMillis(cfg.readTimeoutMs()))
                .header("Accept", "application/json");
        if (!cfg.token().isBlank()) b.header(cfg.authHeader(), cfg.token());
        if (body != null) b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else b.method(method, HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = res.statusCode();
            if (code == 401 || code == 403) {
                log.error("[JUDGE0] {} rejected: HTTP {}. Check JUDGE0_TOKEN and JUDGE0_AUTH_HEADER (the token is not logged).", what, code);
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.AUTH);
            }
            if (code >= 400) {
                log.error("[JUDGE0] {} failed: HTTP {} from {} {}", what, code, method, pathOnly(pathAndQuery));
                throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.INTERNAL);
            }
            return json.readTree(res.body());
        } catch (CodeRunnerUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.TIMEOUT);
        } catch (IOException e) {
            boolean timeout = isTimeout(e);
            log.error("[JUDGE0] {} {} at {}: {}", what, timeout ? "timed out" : "failed (unreachable)", cfg.url(), e.getClass().getSimpleName());
            throw new CodeRunnerUnavailableException(timeout ? CodeRunnerUnavailableException.Reason.TIMEOUT
                    : CodeRunnerUnavailableException.Reason.UNREACHABLE);
        }
    }

    private static String pathOnly(String pathAndQuery) {
        int q = pathAndQuery.indexOf('?');
        return q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException) return true;
            if (t instanceof java.net.SocketTimeoutException) return true;
            if (t instanceof IOException && t.getMessage() != null && t.getMessage().toLowerCase().contains("timed out")) return true;
        }
        return false;
    }
}
