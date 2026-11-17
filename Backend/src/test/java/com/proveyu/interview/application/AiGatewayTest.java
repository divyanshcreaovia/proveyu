package com.proveyu.interview.application;

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
        assertThat(score.path("properties").path("content").path("enum").size()).isEqualTo(5);
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
