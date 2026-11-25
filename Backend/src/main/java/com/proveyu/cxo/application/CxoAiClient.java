package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat-completions client for the CXO portal (OpenAI-compatible: Groq by default), with its own key (CXO_GROQ_API_KEY), separate
 * from the AI interview. Asks for a JSON object and returns it parsed; any failure returns empty so the caller falls back to rules.
 */
@Slf4j
@Component
public class CxoAiClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String key;
    private final String url;
    private final String model;
    private final String reasoningEffort;
    private final Duration timeout;
    private final Semaphore slots;
    /** After a network failure (timeout, reset) the provider is skipped for a short while, so the next calls fall back at once. */
    private volatile long skipUntil;
    private static final long SKIP_AFTER_NETWORK_FAILURE_MS = 30_000;

    public CxoAiClient(ObjectMapper json,
                       @Value("${cxo.ai.api-key:}") String key,
                       @Value("${cxo.ai.url:https://api.groq.com/openai/v1}") String url,
                       @Value("${cxo.ai.model:openai/gpt-oss-120b}") String model,
                       @Value("${cxo.ai.reasoning-effort:low}") String reasoningEffort,
                       @Value("${cxo.ai.timeout-seconds:20}") int timeoutSeconds,
                       @Value("${cxo.ai.max-parallel:4}") int maxParallel) {
        this.json = json;
        this.key = key == null ? "" : key.trim();
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.slots = new Semaphore(Math.max(1, maxParallel), true);
        if (enabled()) log.info("CXO portal: AI planner on ({} at {})", model, this.url);
        else log.info("CXO portal: no CXO_GROQ_API_KEY set, dashboards are planned by rules");
    }

    public boolean enabled() {
        return !key.isEmpty();
    }

    public String model() {
        return model;
    }

    /** One JSON reply, or empty when the AI is off, busy, slow or returns something that is not a JSON object. */
    public Optional<JsonNode> json(String system, String user, double temperature) {
        if (!enabled() || System.currentTimeMillis() < skipUntil) return Optional.empty();
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(10, TimeUnit.SECONDS);
            if (!acquired) return Optional.empty();
            HttpResponse<String> r = send(system, user, temperature, true);
            if (r.statusCode() == 400) r = send(system, user, temperature, false); // model without JSON mode: ask in plain text
            if (r.statusCode() == 429) { // per-minute token limit: wait as long as the provider asks (if short) and try once more
                long waitMs = retryAfterMs(r);
                if (waitMs >= 0 && waitMs <= MAX_RETRY_WAIT_MS) {
                    log.info("CXO AI rate limited, retrying in {} ms", waitMs);
                    Thread.sleep(waitMs);
                    r = send(system, user, temperature, true);
                } else {
                    // a long wait (daily token limit): stop asking for that long (at most 15 minutes) so answers come from rules at once
                    long pause = waitMs < 0 ? 60_000 : Math.min(waitMs, 15 * 60_000L);
                    skipUntil = System.currentTimeMillis() + pause;
                    log.warn("CXO AI limit reached, rule-based answers for the next {} s", pause / 1000);
                }
            }
            if (r.statusCode() / 100 != 2) {
                log.warn("CXO AI call failed: HTTP {} {}", r.statusCode(), abbreviate(r.body()));
                return Optional.empty();
            }
            JsonNode reply = json.readTree(r.body());
            JsonNode usage = reply.path("usage");
            log.info("CXO AI {}: {} prompt + {} completion tokens", model, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
            return parseObject(reply.path("choices").path(0).path("message").path("content").asText(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (java.io.IOException e) { // includes HttpTimeoutException
            skipUntil = System.currentTimeMillis() + SKIP_AFTER_NETWORK_FAILURE_MS;
            log.warn("CXO AI call failed: {} (rule-based answers for the next {} s)", e.toString(), SKIP_AFTER_NETWORK_FAILURE_MS / 1000);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("CXO AI call failed: {}", e.toString());
            return Optional.empty();
        } finally {
            if (acquired) slots.release();
        }
    }

    private HttpResponse<String> send(String system, String user, double temperature, boolean jsonMode) throws Exception {
        ObjectNode body = json.createObjectNode().put("model", model).put("temperature", temperature).put("max_completion_tokens", 3000);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) body.put("reasoning_effort", reasoningEffort);
        body.putArray("messages")
                .add(json.createObjectNode().put("role", "system").put("content", system))
                .add(json.createObjectNode().put("role", "user").put("content", user));
        if (jsonMode) body.putObject("response_format").put("type", "json_object");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/chat/completions")).timeout(timeout)
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static final long MAX_RETRY_WAIT_MS = 12_000;
    private static final Pattern TRY_AGAIN = Pattern.compile("try again in (?:(\\d+)m)?([\\d.]+)(ms|s)");

    /** How long a 429 asks us to wait: the Retry-After header, else "try again in 5.6s" in the body; -1 when unknown. */
    static long retryAfterMs(HttpResponse<String> r) {
        Optional<String> h = r.headers().firstValue("retry-after");
        try {
            if (h.isPresent()) return Math.round(Double.parseDouble(h.get().trim()) * 1000) + 250;
        } catch (NumberFormatException ignored) { /* fall through to the body */ }
        Matcher m = TRY_AGAIN.matcher(r.body() == null ? "" : r.body());
        if (!m.find()) return -1;
        double v = Double.parseDouble(m.group(2));
        long ms = Math.round("ms".equals(m.group(3)) ? v : v * 1000) + (m.group(1) == null ? 0 : Long.parseLong(m.group(1)) * 60_000);
        return ms + 250;
    }

    /** The first {...} block of the reply (models sometimes wrap JSON in prose or code fences). */
    private Optional<JsonNode> parseObject(String content) {
        int start = content.indexOf('{'), end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return Optional.empty();
        try {
            JsonNode n = json.readTree(content.substring(start, end + 1));
            return n.isObject() ? Optional.of(n) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
