package com.proveyu.student.prep.application;

import com.proveyu.student.prep.application.Judge0Client.Judgement;
import com.proveyu.student.prep.application.Judge0Client.Submission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Judge0Client against the scripted FakeJudge0 server: request shape, batching, polling, and error classification. */
class Judge0ClientTest {

    private static final String TOKEN = "secret-token-value";

    private FakeJudge0 fake;

    @BeforeEach
    void start() throws IOException {
        fake = new FakeJudge0();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    static CodeExecutionProperties props(String url, long pollTimeoutMs, int readTimeoutMs, int maxBatch) {
        CodeExecutionProperties.Judge0 j = new CodeExecutionProperties.Judge0(url, TOKEN, "X-Auth-Token", 20L, pollTimeoutMs, 4,
                1000, readTimeoutMs, 3.0, 8.0, 262_144L, 10.0, 15.0, 524_288L, maxBatch);
        return new CodeExecutionProperties("judge0", 5, 2000L, 65_536, 65_536, 65_536, j, null);
    }

    private Judge0Client client() {
        return new Judge0Client(props(fake.url(), 5_000, 5_000, 20));
    }

    private static Submission sub(String source, String stdin) {
        return new Submission(source, stdin, 71, 3.0, 8.0, 262_144L);
    }

    @Test
    void batchRequestIsBase64_authenticatedAndHasNoWait() {
        fake.script = s -> FakeJudge0.judgement(3, "Accepted", "5\n", "", null, null);
        List<String> tokens = client().createBatch(List.of(sub("print(int(input())+2)\n", "3\n")));

        assertThat(tokens).hasSize(1);
        FakeJudge0.Captured post = fake.posts().get(0);
        assertThat(post.path()).isEqualTo("/submissions/batch");
        assertThat(post.query()).contains("base64_encoded=true").doesNotContain("wait");
        assertThat(post.headers()).containsEntry("x-auth-token", TOKEN);
        // Judge0 (Rails) needs a fixed-length body: a chunked body arrived empty and was rejected with 400 in production
        assertThat(post.headers()).containsKey("content-length").doesNotContainKey("transfer-encoding");
        assertThat(post.body()).contains(FakeJudge0.b64("print(int(input())+2)\n")).doesNotContain("print(int");
        assertThat(post.body()).contains("\"cpu_time_limit\":3.0", "\"memory_limit\":262144");
    }

    @Test
    void pollsUntilEverySubmissionIsFinished() {
        fake.pollsBeforeDone = 2;
        fake.script = s -> FakeJudge0.judgement(3, "Accepted", "5\n", "", null, null);
        Judge0Client client = client();
        List<String> tokens = client.createBatch(List.of(sub("x", "1")));

        List<Judgement> out = client.awaitBatch(tokens);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).verdict()).isEqualTo(Judge0Verdict.ACCEPTED);
        assertThat(out.get(0).stdout()).isEqualTo("5\n");
        assertThat(fake.gets()).hasSize(3); // two "processing" answers, then the final one
        assertThat(fake.gets().get(0).query()).contains("base64_encoded=true", "fields=");
    }

    @Test
    void largeBatchesAreSplitToTheConfiguredSize() {
        Judge0Client client = new Judge0Client(props(fake.url(), 5_000, 5_000, 2));
        List<String> tokens = client.createBatch(List.of(sub("a", "1"), sub("a", "2"), sub("a", "3"), sub("a", "4"), sub("a", "5")));

        assertThat(tokens).hasSize(5).doesNotHaveDuplicates();
        assertThat(fake.posts()).hasSize(3); // 2 + 2 + 1
    }

    @Test
    void pollTimeoutIsReportedAsTimeout() {
        fake.neverFinish = true;
        Judge0Client client = new Judge0Client(props(fake.url(), 300, 5_000, 20));
        List<String> tokens = client.createBatch(List.of(sub("x", "1")));

        assertThatThrownBy(() -> client.awaitBatch(tokens))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.TIMEOUT));
    }

    @Test
    void badTokenIsReportedAsAuthAndTheTokenIsNotInTheMessage() {
        fake.httpStatus = 401;
        assertThatThrownBy(() -> client().createBatch(List.of(sub("x", "1"))))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.AUTH);
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                });
    }

    @Test
    void forbiddenIsAlsoAuth() {
        fake.httpStatus = 403;
        assertThatThrownBy(() -> client().languageCount())
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.AUTH));
    }

    @Test
    void serverErrorIsReportedAsInternal() {
        fake.httpStatus = 500;
        assertThatThrownBy(() -> client().createBatch(List.of(sub("x", "1"))))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.INTERNAL));
    }

    @Test
    void slowJudgeHitsTheReadTimeout() {
        fake.delayMs = 1_500;
        Judge0Client client = new Judge0Client(props(fake.url(), 5_000, 200, 20));
        assertThatThrownBy(() -> client.createBatch(List.of(sub("x", "1"))))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.TIMEOUT));
    }

    @Test
    void closedPortIsReportedAsUnreachable() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        Judge0Client client = new Judge0Client(props("http://127.0.0.1:" + port, 5_000, 1_000, 20));
        assertThatThrownBy(() -> client.languageCount())
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.UNREACHABLE));
    }

    @Test
    void healthCountsLanguages() {
        assertThat(client().languageCount()).isEqualTo(2);
    }
}
