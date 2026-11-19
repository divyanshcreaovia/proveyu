package com.proveyu.student.interview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGatewayTest {

    private final AiGateway noCloud = gateway("");
    private final AiGateway withCloud = gateway("test-key");

    private static AiGateway gateway(String key) {
        return new AiGateway(new ObjectMapper(), "http://localhost:1", "local-small", "", "http://localhost:1", "http://localhost:1", 5, 2,
                key, "https://api.example.com/v1/", "cloud-model", "", "whisper-x", "", "", "low", 4, true);
    }

    @Test
    void strictSchemaListsEveryPropertyAndClosesObjects() {
        JsonNode s = noCloud.strictSchema(InterviewPrompts.planSchema(4));
        assertThat(s.toString()).doesNotContain("minItems").doesNotContain("maxItems");
        assertThat(s.path("additionalProperties").asBoolean(true)).isFalse();
        JsonNode item = s.path("properties").path("questions").path("items");
        if (item.path("type").asText().equals("object")) {
            assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(item.path("required").size()).isEqualTo(item.path("properties").size());
        }
        JsonNode score = noCloud.strictSchema(InterviewPrompts.SCORE_SCHEMA);
        assertThat(score.path("required").size()).isEqualTo(score.path("properties").size()).isEqualTo(7);
        assertThat(score.path("properties").path("content").path("enum").size()).isEqualTo(6); // 0..5
    }

    @Test
    void modelNamesFollowTheActiveProvider() {
        assertThat(noCloud.model()).isEqualTo("local-small");
        assertThat(noCloud.evalModel()).isEqualTo("local-small");
        assertThat(withCloud.model()).isEqualTo("cloud-model");
        assertThat(withCloud.evalModel()).isEqualTo("cloud-model");
        assertThat(withCloud.describe()).contains("https://api.example.com/v1").contains("whisper-x");
    }

    /** 16 kHz mono 16-bit WAV with the given samples. */
    private static byte[] wav(short[] samples) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + samples.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + samples.length * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(samples.length * 2);
        for (short x : samples) b.putShort(x);
        return b.array();
    }

    @Test
    void silenceAndRoomNoiseAreSilentButSpeechIsNot() {
        short[] zeros = new short[32000];
        java.util.Random r = new java.util.Random(1);
        short[] hiss = new short[32000];
        for (int i = 0; i < hiss.length; i++) hiss[i] = (short) (r.nextGaussian() * 60);   // about -55 dBFS
        short[] voice = hiss.clone();
        for (int i = 8000; i < 16000; i++) voice[i] += (short) (3000 * Math.sin(i * 2 * Math.PI * 220 / 16000)); // about -20 dBFS
        assertThat(AiGateway.isSilent(wav(zeros))).isTrue();
        assertThat(AiGateway.isSilent(wav(hiss))).isTrue();
        assertThat(AiGateway.isSilent(wav(voice))).isFalse();
        assertThat(AiGateway.isSilent(new byte[]{1, 2, 3})).isFalse(); // not a WAV: let the speech service decide
    }

    /** Fake OpenAI-compatible server: "key-a" is always rate limited, "key-b" answers. Records which key each call used. */
    @Test
    void aRateLimitedKeyFallsBackToTheNextKeyAndRests() throws Exception {
        java.util.List<String> used = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            String key = ex.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length());
            used.add(key);
            byte[] body = "key-a".equals(key) ? "{\"error\":\"rate limit\"}".getBytes()
                    : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{}}".getBytes();
            if ("key-a".equals(key)) ex.getResponseHeaders().add("retry-after", "60");
            ex.sendResponseHeaders("key-a".equals(key) ? 429 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            AiGateway g = new AiGateway(new ObjectMapper(), "http://localhost:1", "local-small", "", "http://localhost:1", "http://localhost:1", 5, 2,
                    " key-a , key-b ", url, "cloud-model", "", "whisper-x", "", "", "low", 4, false);
            String schema = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}";

            assertThat(g.chatJson("s", "u", 0.2, schema).path("ok").asBoolean()).isTrue();
            assertThat(used).containsExactly("key-a", "key-b");

            // key-a now rests for its retry-after, so the next call goes straight to key-b
            assertThat(g.chatJson("s", "u", 0.2, schema).path("ok").asBoolean()).isTrue();
            assertThat(used).containsExactly("key-a", "key-b", "key-b");
        } finally {
            server.stop(0);
        }
    }

    /** Groq's "json_validate_failed" is transient: two failures in a row are retried and the call still ends on the cloud model. */
    @Test
    void aFailedJsonCheckIsRetriedTwice() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            boolean fail = calls.incrementAndGet() <= 2;
            byte[] body = fail ? "{\"error\":{\"code\":\"json_validate_failed\",\"message\":\"Failed to validate JSON.\"}}".getBytes()
                    : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{}}".getBytes();
            ex.sendResponseHeaders(fail ? 400 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            AiGateway g = new AiGateway(new ObjectMapper(), "http://localhost:1", "local-small", "", "http://localhost:1", "http://localhost:1", 5, 2,
                    "key-a", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "cloud-model", "", "whisper-x", "", "", "low", 4, false);
            assertThat(g.chatJson("s", "u", 0.2, "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}").path("ok").asBoolean()).isTrue();
            assertThat(calls.get()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    /** Three healthy keys are used in turn, so no single key carries all the load. */
    @Test
    void severalKeysAreUsedInTurn() throws Exception {
        java.util.List<String> used = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            used.add(ex.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length()));
            byte[] body = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{}}".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            AiGateway g = new AiGateway(new ObjectMapper(), "http://localhost:1", "local-small", "", "http://localhost:1", "http://localhost:1", 5, 2,
                    "k1,k2,k3", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "cloud-model", "", "whisper-x", "", "", "low", 4, false);
            String schema = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}";
            for (int i = 0; i < 4; i++) g.chatJson("s", "u", 0.2, schema);
            assertThat(used).containsExactly("k1", "k2", "k3", "k1");
        } finally {
            server.stop(0);
        }
    }

    /** AI_LOCAL_MODELS_ENABLED=false: nothing local is ever called; without a Groq key the AI says it is not set up. */
    @Test
    void localModelsOffNeverCallsLocalServices() {
        AiGateway off = new AiGateway(new ObjectMapper(), "http://localhost:1", "local-small", "", "http://localhost:1", "http://localhost:1", 5, 2,
                "", "https://api.example.com/v1", "cloud-model", "", "whisper-x", "", "", "low", 4, false);
        assertThat(off.localModels()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> off.chatJson("s", "u", 0.2, "{\"type\":\"object\",\"properties\":{}}"))
                .hasMessageContaining("not set up");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> off.speak("Hello")).hasMessageContaining("turned off");
        assertThat(off.health().textToSpeech()).isFalse();
        assertThat(off.health().llm()).isFalse();
        assertThat(withCloud.localModels()).isTrue();
    }

    @Test
    void segmentsWhisperMarksAsSilenceAreDropped() throws Exception {
        ObjectMapper m = new ObjectMapper();
        JsonNode reply = m.readTree("{\"text\":\" I built a dashboard. Thank you.\",\"segments\":["
                + "{\"text\":\" I built a dashboard.\",\"no_speech_prob\":0.01,\"avg_logprob\":-0.2},"
                + "{\"text\":\" Thank you.\",\"no_speech_prob\":0.9,\"avg_logprob\":-1.4}]}");
        assertThat(AiGateway.spokenText(reply).trim()).isEqualTo("I built a dashboard.");
        assertThat(AiGateway.spokenText(m.readTree("{\"text\":\"plain json\"}"))).isEqualTo("plain json");
    }
}
