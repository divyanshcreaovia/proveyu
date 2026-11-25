package com.proveyu.student.prep.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scripted stand-in for the Judge0 CE API (POST /submissions/batch, GET /submissions/batch, GET /languages), built on the JDK's
 * own HttpServer so the tests need no network, no Docker and no extra dependency. Every request is recorded.
 */
final class FakeJudge0 implements AutoCloseable {

    /** What the fake received for one submission, decoded from base64. */
    record Sub(String source, String stdin, int languageId, JsonNode raw) {}

    /** One request as the fake saw it. */
    record Captured(String method, String path, String query, Map<String, String> headers, String body) {}

    @FunctionalInterface
    interface Script {
        /** Returns the judgement for one submission: keys id, description, stdout, stderr, compile_output, message (plain text). */
        Map<String, Object> judge(Sub s);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    final HttpServer server;
    final List<Captured> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();
    private final Map<String, Integer> polls = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    volatile Script script = s -> judgement(3, "Accepted", "", "", "", "");
    /** > 0: every request answers with this HTTP status (for 401, 403, 500 tests). */
    volatile int httpStatus = 0;
    /** > 0: every request sleeps this long before answering (for the read-timeout test). */
    volatile long delayMs = 0;
    /** GET answers "still processing" this many times per token before the real result. */
    volatile int pollsBeforeDone = 0;
    /** GET never finishes (for the poll-timeout test). */
    volatile boolean neverFinish = false;

    FakeJudge0() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Captured> posts() {
        return requests.stream().filter(c -> "POST".equals(c.method())).toList();
    }

    List<Captured> gets() {
        return requests.stream().filter(c -> "GET".equals(c.method())).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static Map<String, Object> judgement(int id, String desc, String stdout, String stderr, String compileOutput, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("description", desc);
        m.put("stdout", stdout);
        m.put("stderr", stderr);
        m.put("compile_output", compileOutput);
        m.put("message", message);
        return m;
    }

    static String b64(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            byte[] raw = ex.getRequestBody().readAllBytes();
            String body = new String(raw, StandardCharsets.UTF_8);
            Map<String, String> headers = new HashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getRawQuery();
            requests.add(new Captured(ex.getRequestMethod(), path, query, headers, body));

            if (delayMs > 0) sleep(delayMs);
            if (httpStatus > 0) {
                send(ex, httpStatus, "{\"error\":\"fake failure\"}");
                return;
            }
            if ("POST".equals(ex.getRequestMethod()) && "/submissions/batch".equals(path)) {
                ArrayNode out = JSON.createArrayNode();
                for (JsonNode s : JSON.readTree(body).path("submissions")) {
                    String token = "tok" + seq.incrementAndGet();
                    Sub sub = new Sub(decode(s.path("source_code")), decode(s.path("stdin")), s.path("language_id").asInt(), s);
                    results.put(token, script.judge(sub));
                    ObjectNode t = JSON.createObjectNode();
                    t.put("token", token);
                    out.add(t);
                }
                send(ex, 201, JSON.writeValueAsString(out));
            } else if ("GET".equals(ex.getRequestMethod()) && "/submissions/batch".equals(path)) {
                ArrayNode subs = JSON.createArrayNode();
                String tokens = queryParam(query, "tokens");
                for (String token : tokens.split(",")) subs.add(pollEntry(token));
                ObjectNode root = JSON.createObjectNode();
                root.set("submissions", subs);
                send(ex, 200, JSON.writeValueAsString(root));
            } else if ("GET".equals(ex.getRequestMethod()) && "/languages".equals(path)) {
                send(ex, 200, "[{\"id\":71,\"name\":\"Python (3.8.1)\"},{\"id\":62,\"name\":\"Java (OpenJDK 13.0.1)\"}]");
            } else {
                send(ex, 404, "{\"error\":\"not found\"}");
            }
        } catch (Exception e) {
            send(ex, 500, "{\"error\":\"fake crashed\"}");
        }
    }

    private ObjectNode pollEntry(String token) {
        ObjectNode n = JSON.createObjectNode();
        n.put("token", token);
        int seen = polls.merge(token, 1, Integer::sum);
        Map<String, Object> r = results.get(token);
        if (neverFinish || r == null || seen <= pollsBeforeDone) {
            n.set("status", status(2, "Processing"));
            return n;
        }
        n.set("status", status((Integer) r.get("id"), (String) r.get("description")));
        n.put("stdout", b64OrNull((String) r.get("stdout")));
        n.put("stderr", b64OrNull((String) r.get("stderr")));
        n.put("compile_output", b64OrNull((String) r.get("compile_output")));
        n.put("message", b64OrNull((String) r.get("message")));
        return n;
    }

    private ObjectNode status(int id, String desc) {
        ObjectNode s = JSON.createObjectNode();
        s.put("id", id);
        s.put("description", desc);
        return s;
    }

    /** Judge0 ends its base64 text fields with a newline; the fake does the same so the client is tested against it. */
    private static String b64OrNull(String plain) {
        return plain == null ? null : b64(plain) + "\n";
    }

    private static String decode(JsonNode n) {
        if (n == null || n.isNull()) return "";
        return new String(Base64.getDecoder().decode(n.asText()), StandardCharsets.UTF_8);
    }

    private static String queryParam(String query, String name) {
        if (query == null) return "";
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(name)) return java.net.URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
