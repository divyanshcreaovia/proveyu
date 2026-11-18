package com.proveyu.student.interview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proveyu.student.interview.domain.InterviewModel.ServiceHealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.proveyu.shared.error.DomainException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The AI services behind one class.
 * <ul>
 *   <li>Cloud (optional): any OpenAI-compatible API, Groq by default, for the LLM and speech to text. On when an API key is set.</li>
 *   <li>Local: Ollama (LLM), whisper.cpp server (speech to text) and Piper (text to speech, always local).</li>
 * </ul>
 * With a key, every LLM / speech-to-text call goes to the cloud first and falls back to the local service when the cloud fails or
 * hits its rate limit. Without a key, only the local services are used. Moving to an own model server later is a config change:
 * point {@code interview.cloud.url} at it (Ollama and vLLM speak the same OpenAI-compatible API) or remove the key.
 * Semaphores cap how many requests hit each server at once; extra requests wait in line instead of overloading it.
 */
@Slf4j
@Component
public class AiGateway {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String llmUrl;
    private final String model;
    private final String evalModel;
    private final String sttUrl;
    private final String ttsUrl;
    private final Duration timeout;
    private final Semaphore slots;
    private final Cloud cloud;
    private final boolean fallbackLocal;
    private final Semaphore cloudSlots;
    private volatile long healthAt;
    private volatile ServiceHealth healthCache;

    /** OpenAI-compatible cloud settings. Off when the key is blank. */
    record Cloud(String url, String key, String model, String evalModel, String sttModel, String sttLanguage, String sttPrompt, String reasoningEffort) {
        boolean on() {
            return key != null && !key.isBlank();
        }

        boolean sttOn() {
            return on() && sttModel != null && !sttModel.isBlank();
        }
    }

    public AiGateway(ObjectMapper json,
                     @Value("${interview.llm.url:http://localhost:11434}") String llmUrl,
                     @Value("${interview.llm.model:qwen3:1.7b}") String model,
                     @Value("${interview.llm.eval-model:}") String evalModel,
                     @Value("${interview.stt.url:http://localhost:8178}") String sttUrl,
                     @Value("${interview.tts.url:http://localhost:5002}") String ttsUrl,
                     @Value("${interview.ai.timeout-seconds:180}") int timeoutSeconds,
                     @Value("${interview.ai.max-parallel:2}") int maxParallel,
                     @Value("${interview.cloud.api-key:}") String cloudKey,
                     @Value("${interview.cloud.url:https://api.groq.com/openai/v1}") String cloudUrl,
                     @Value("${interview.cloud.model:openai/gpt-oss-20b}") String cloudModel,
                     @Value("${interview.cloud.eval-model:}") String cloudEvalModel,
                     @Value("${interview.cloud.stt-model:whisper-large-v3-turbo}") String cloudSttModel,
                     @Value("${interview.cloud.stt-language:}") String cloudSttLanguage,
                     @Value("${interview.cloud.stt-prompt:}") String cloudSttPrompt,
                     @Value("${interview.cloud.reasoning-effort:low}") String reasoningEffort,
                     @Value("${interview.cloud.max-parallel:4}") int cloudParallel,
                     @Value("${interview.cloud.fallback-local:true}") boolean fallbackLocal) {
        this.json = json;
        this.llmUrl = trim(llmUrl);
        this.model = model;
        this.evalModel = evalModel == null || evalModel.isBlank() ? model : evalModel;
        this.sttUrl = trim(sttUrl);
        this.ttsUrl = trim(ttsUrl);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.slots = new Semaphore(Math.max(1, maxParallel), true);
        this.cloud = new Cloud(trim(cloudUrl), cloudKey == null ? "" : cloudKey.trim(), cloudModel,
                cloudEvalModel == null || cloudEvalModel.isBlank() ? cloudModel : cloudEvalModel, cloudSttModel,
                cloudSttLanguage, cloudSttPrompt, reasoningEffort);
        this.fallbackLocal = fallbackLocal;
        this.cloudSlots = new Semaphore(Math.max(1, cloudParallel), true);
        if (cloud.on()) log.info("AI interview: cloud LLM {} / {} and speech-to-text {} at {} (local fallback {})", cloud.model(), cloud.evalModel(),
                cloud.sttOn() ? cloud.sttModel() : "local", cloud.url(), fallbackLocal ? "on" : "off");
        else log.info("AI interview: local services only (no cloud API key set)");
    }

    /** Model for the live interview (questions, follow-ups): fast. */
    public String model() {
        return cloud.on() ? cloud.model() : model;
    }

    /** Model for scoring and the report, which run in the background: can be larger and slower. */
    public String evalModel() {
        return cloud.on() ? cloud.evalModel() : evalModel;
    }

    /** Live-interview call (questions, follow-ups). */
    public JsonNode chatJson(String system, String user, double temperature, String jsonSchema) {
        return chat(false, system, user, temperature, jsonSchema);
    }

    /** Background call (scoring, report summary). */
    public JsonNode evalJson(String system, String user, double temperature, String jsonSchema) {
        return chat(true, system, user, temperature, jsonSchema);
    }

    private JsonNode chat(boolean eval, String system, String user, double temperature, String jsonSchema) {
        if (cloud.on()) {
            try {
                return cloudChat(eval ? cloud.evalModel() : cloud.model(), system, user, temperature, jsonSchema);
            } catch (DomainException e) {
                if (!fallbackLocal) throw e;
                log.warn("Cloud LLM failed ({}), using the local model", e.getMessage());
            }
        }
        return localChat(eval ? evalModel : model, system, user, temperature, jsonSchema);
    }

    /**
     * OpenAI-compatible chat completion with strict JSON-schema output (supported on Groq by gpt-oss and qwen models).
     * A rate limit (429) with a short retry-after is waited out once.
     */
    JsonNode cloudChat(String useModel, String system, String user, double temperature, String jsonSchema) {
        ObjectNode body = json.createObjectNode().put("model", useModel).put("temperature", temperature).put("max_completion_tokens", 4096);
        if (cloud.reasoningEffort() != null && !cloud.reasoningEffort().isBlank()) body.put("reasoning_effort", cloud.reasoningEffort());
        body.putArray("messages").add(json.createObjectNode().put("role", "system").put("content", system))
                .add(json.createObjectNode().put("role", "user").put("content", user));
        ObjectNode format = body.putObject("response_format").put("type", "json_schema");
        format.putObject("json_schema").put("name", "reply").put("strict", true).set("schema", strictSchema(jsonSchema));
        String content = withCloudSlot(() -> {
            HttpResponse<String> r = sendCloud(HttpRequest.newBuilder(URI.create(cloud.url() + "/chat/completions")).timeout(timeout)
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + cloud.key())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
            JsonNode reply = json.readTree(r.body());
            JsonNode usage = reply.path("usage");
            log.info("Cloud LLM {}: {} prompt + {} completion tokens", useModel, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
            JsonNode choice = reply.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) throw badReply();
            return choice.path("message").path("content").asText("");
        });
        return parseObject(content);
    }

    /**
     * Strict structured output needs every property listed in "required" and "additionalProperties": false on every object.
     * minItems / maxItems are dropped: not every provider accepts them, and the readers already trim or top up lists.
     */
    JsonNode strictSchema(String jsonSchema) {
        try {
            JsonNode root = json.readTree(jsonSchema);
            strict(root);
            return root;
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad schema", e);
        }
    }

    private void strict(JsonNode node) {
        if (!(node instanceof ObjectNode o)) {
            if (node instanceof ArrayNode a) a.forEach(this::strict);
            return;
        }
        o.remove("minItems");
        o.remove("maxItems");
        if ("object".equals(o.path("type").asText()) && o.has("properties")) {
            o.put("additionalProperties", false);
            ArrayNode required = o.putArray("required");
            for (Iterator<String> it = o.get("properties").fieldNames(); it.hasNext(); ) required.add(it.next());
        }
        o.forEach(this::strict);
    }

    /**
     * Ollama: asks the LLM and returns its reply parsed as a JSON object. The JSON schema constrains generation, so even a small
     * model returns valid, complete structure. Thinking mode off: faster, and the reply is only JSON.
     */
    private JsonNode localChat(String useModel, String system, String user, double temperature, String jsonSchema) {
        ObjectNode body = json.createObjectNode().put("model", useModel).put("stream", false).put("think", false);
        try {
            body.set("format", json.readTree(jsonSchema));
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad schema", e);
        }
        body.putArray("messages").add(json.createObjectNode().put("role", "system").put("content", system))
                .add(json.createObjectNode().put("role", "user").put("content", user));
        body.putObject("options").put("temperature", temperature).put("num_ctx", 4096);
        String content = withSlot(() -> {
            HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(llmUrl + "/api/chat")).timeout(timeout)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
            return json.readTree(r.body()).path("message").path("content").asText("");
        });
        return parseObject(content);
    }

    /** Speech to text: 16 kHz mono WAV in, plain text out. Cloud Whisper first when configured, else / on failure whisper.cpp. */
    public String transcribe(byte[] wav) {
        // Whisper invents text for silence ("Thank you.", "Thanks for watching!"), so silent audio is never sent to it.
        if (isSilent(wav)) return "";
        if (cloud.sttOn()) {
            try {
                return cloudTranscribe(wav);
            } catch (DomainException e) {
                if (!fallbackLocal) throw e;
                log.warn("Cloud speech-to-text failed ({}), using local whisper", e.getMessage());
            }
        }
        return localTranscribe(wav);
    }

    private String cloudTranscribe(byte[] wav) {
        String boundary = "----proveyu" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, boundary, "model", cloud.sttModel());
        part(out, boundary, "response_format", "verbose_json");
        part(out, boundary, "temperature", "0");
        if (cloud.sttLanguage() != null && !cloud.sttLanguage().isBlank()) part(out, boundary, "language", cloud.sttLanguage().trim());
        if (cloud.sttPrompt() != null && !cloud.sttPrompt().isBlank()) part(out, boundary, "prompt", cloud.sttPrompt().trim());
        filePart(out, boundary, wav);
        return withCloudSlot(() -> {
            HttpResponse<String> r = sendCloud(HttpRequest.newBuilder(URI.create(cloud.url() + "/audio/transcriptions")).timeout(timeout)
                    .header("Authorization", "Bearer " + cloud.key())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
            return cleanTranscript(spokenText(json.readTree(r.body())));
        });
    }

    /**
     * Joins the segments Whisper believes are speech. A segment counts as silence when Whisper rates it likely non-speech and is
     * unsure of the words (OpenAI's own rule: no_speech_prob > 0.6 and avg_logprob < -1); those are where it makes text up.
     */
    static String spokenText(JsonNode reply) {
        JsonNode segments = reply.path("segments");
        if (!segments.isArray() || segments.isEmpty()) return reply.path("text").asText("");
        StringBuilder out = new StringBuilder();
        for (JsonNode seg : segments) {
            boolean silence = seg.path("no_speech_prob").asDouble(0) > 0.6 && seg.path("avg_logprob").asDouble(0) < -1;
            if (!silence) out.append(seg.path("text").asText("")).append(' ');
        }
        return out.toString();
    }

    /**
     * True when the recording holds no speech: no 30 ms stretch louder than about -40 dBFS. Room noise sits far below that and
     * speech well above it (the browser also applies automatic gain). Only 16-bit PCM WAV is inspected; anything else counts as sound.
     */
    static boolean isSilent(byte[] wav) {
        int data = dataOffset(wav);
        if (data < 0 || wav[34] != 16) return false;
        int frame = 480 * 2; // 30 ms at 16 kHz, 2 bytes per sample
        double threshold = 0.01 * 32768;
        for (int start = data; start + frame <= wav.length; start += frame) {
            double sum = 0;
            for (int i = start; i < start + frame; i += 2) {
                int sample = (short) ((wav[i] & 0xff) | (wav[i + 1] << 8));
                sum += (double) sample * sample;
            }
            if (Math.sqrt(sum / 480) > threshold) return false;
        }
        return true;
    }

    /** Start of the PCM bytes in a RIFF/WAVE file (after the "data" chunk header), or -1. */
    private static int dataOffset(byte[] wav) {
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F' || wav[8] != 'W' || wav[9] != 'A') return -1;
        int pos = 12;
        while (pos + 8 <= wav.length) {
            int size = (wav[pos + 4] & 0xff) | (wav[pos + 5] & 0xff) << 8 | (wav[pos + 6] & 0xff) << 16 | (wav[pos + 7] & 0xff) << 24;
            if (wav[pos] == 'd' && wav[pos + 1] == 'a' && wav[pos + 2] == 't' && wav[pos + 3] == 'a') return pos + 8;
            if (size < 0) return -1;
            pos += 8 + size + (size & 1);
        }
        return -1;
    }

    private String localTranscribe(byte[] wav) {
        String boundary = "----proveyu" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, boundary, "response_format", "json");
        part(out, boundary, "temperature", "0.0");
        filePart(out, boundary, wav);
        return withSlot(() -> {
            HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(sttUrl + "/inference")).timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
            return cleanTranscript(json.readTree(r.body()).path("text").asText(""));
        });
    }

    /** Piper: text in, WAV out. Runs on CPU and is quick, so it does not take a model slot. */
    public byte[] speak(String text) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(ttsUrl + "/")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(json.createObjectNode().put("text", text)))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() >= 300) throw unavailable("Voice service returned " + r.statusCode());
            return r.body();
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("Voice service is not reachable");
        }
    }

    /** Cached for 30 seconds: the student home page asks on every visit and the cloud check is a network round trip. */
    public ServiceHealth health() {
        ServiceHealth h = healthCache;
        if (h != null && System.currentTimeMillis() - healthAt < 30_000) return h;
        boolean cloudUp = cloud.on() && cloudUp();
        boolean localLlm = (!cloud.on() || fallbackLocal) && up(llmUrl + "/api/tags");
        boolean localStt = (!cloud.sttOn() || fallbackLocal) && up(sttUrl + "/");
        h = new ServiceHealth(cloudUp || localLlm, (cloudUp && cloud.sttOn()) || localStt, up(ttsUrl + "/voices"));
        healthCache = h;
        healthAt = System.currentTimeMillis();
        return h;
    }

    /** Which services answer: for logs and the admin. */
    public String describe() {
        return cloud.on() ? "cloud " + cloud.url() + " (" + cloud.model() + ", " + (cloud.sttOn() ? cloud.sttModel() : "local whisper") + ")" : "local";
    }

    private boolean cloudUp() {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(cloud.url() + "/models")).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + cloud.key()).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** whisper marks silence and noise as "[BLANK_AUDIO]", "(music)" etc. Those are not words the student said. */
    static String cleanTranscript(String raw) {
        return raw.replaceAll("\\[[^\\]]*]", " ").replaceAll("\\((?i:music|silence|noise|inaudible|background noise)[^)]*\\)", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** Small models sometimes wrap JSON in prose or code fences; take the outermost object. */
    JsonNode parseObject(String content) {
        int a = content.indexOf('{'), b = content.lastIndexOf('}');
        if (a < 0 || b <= a) throw new DomainException("The AI reply was not valid JSON", HttpStatus.BAD_GATEWAY, "AI_BAD_REPLY");
        try {
            return json.readTree(content.substring(a, b + 1));
        } catch (Exception e) {
            throw new DomainException("The AI reply was not valid JSON", HttpStatus.BAD_GATEWAY, "AI_BAD_REPLY");
        }
    }

    private boolean up(String url) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 300) {
            log.warn("AI service {} returned {}: {}", req.uri(), r.statusCode(), r.body().length() > 300 ? r.body().substring(0, 300) : r.body());
            throw unavailable("AI service returned " + r.statusCode());
        }
        return r;
    }

    /** Cloud call with one wait-and-retry on a rate limit whose retry-after is short; a longer limit fails over to local. */
    private HttpResponse<String> sendCloud(HttpRequest req) throws Exception {
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 429 && attempt == 0) {
                double wait = r.headers().firstValue("retry-after").map(v -> {
                    try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return 99.0; }
                }).orElse(99.0);
                if (wait <= 8) {
                    log.info("Cloud AI rate limit, retrying in {} s", wait);
                    Thread.sleep((long) (wait * 1000) + 200);
                    continue;
                }
            }
            String b = r.body() == null ? "" : r.body();
            // Structured output sometimes fails the provider's own schema check; Groq's advice is to just try again.
            if (r.statusCode() == 400 && attempt == 0 && (b.contains("json_validate_failed") || b.contains("Failed to validate JSON"))) {
                log.info("Cloud AI reply failed its JSON check, retrying once");
                continue;
            }
            if (r.statusCode() >= 300) {
                log.warn("Cloud AI {} returned {}: {}", req.uri().getPath(), r.statusCode(), b.length() > 300 ? b.substring(0, 300) : b);
                throw unavailable(r.statusCode() == 429 ? "Cloud AI rate limit reached" : r.statusCode() == 401 ? "Cloud AI key is not valid"
                        : "Cloud AI returned " + r.statusCode());
            }
            return r;
        }
    }

    private interface Call<T> { T run() throws Exception; }

    private <T> T withCloudSlot(Call<T> call) {
        return guarded(cloudSlots, call);
    }

    private <T> T withSlot(Call<T> call) {
        return guarded(slots, call);
    }

    private <T> T guarded(Semaphore slots, Call<T> call) {
        boolean got = false;
        try {
            got = slots.tryAcquire(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!got) throw unavailable("The AI interviewer is busy. Please try again in a minute.");
            return call.run();
        } catch (DomainException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Interrupted");
        } catch (java.net.http.HttpTimeoutException e) {
            throw unavailable("The AI took too long to reply. Please try again.");
        } catch (Exception e) {
            log.warn("AI call failed: {}", e.toString());
            throw unavailable("The AI service is not reachable right now. Please try again later.");
        } finally {
            if (got) slots.release();
        }
    }

    private static DomainException badReply() {
        return new DomainException("The AI reply was cut off", HttpStatus.BAD_GATEWAY, "AI_BAD_REPLY");
    }

    private static DomainException unavailable(String msg) {
        return new DomainException(msg, HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE");
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String value) {
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void filePart(ByteArrayOutputStream out, String boundary, byte[] wav) {
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"answer.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.writeBytes(wav);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
