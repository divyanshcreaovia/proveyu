package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import com.proveyu.student.prep.application.CodeExecutionService.Output;
import com.proveyu.student.prep.application.CodeExecutionService.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.proveyu.student.prep.application.FakeJudge0.judgement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Judge0CodeExecutionService: status-to-output mapping, validation, limits, concurrency. Judge0 is the scripted FakeJudge0. */
class Judge0CodeExecutionServiceTest {

    private FakeJudge0 fake;

    @BeforeEach
    void start() throws IOException {
        fake = new FakeJudge0();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    private Judge0CodeExecutionService service(int maxParallel, long queueWaitMs, double cpuLimit) {
        CodeExecutionProperties.Judge0 j = new CodeExecutionProperties.Judge0(fake.url(), "tok", "X-Auth-Token", 20L, 5_000L, maxParallel,
                1000, 5_000, cpuLimit, 8.0, 262_144L, 10.0, 15.0, 524_288L, 20);
        CodeExecutionProperties props = new CodeExecutionProperties("judge0", 0, queueWaitMs, 65_536, 65_536, 65_536, j, null);
        return new Judge0CodeExecutionService(new Judge0Client(props), props);
    }

    private Judge0CodeExecutionService service() {
        return service(4, 2_000, 3.0);
    }

    @Test
    void acceptedRunReturnsTheProgramOutput() {
        fake.script = s -> judgement(3, "Accepted", "5\n", "", null, null);
        Result r = service().run("python", "print(5)", List.of("3\n"));

        assertThat(r.compileError()).isNull();
        assertThat(r.outputs()).hasSize(1);
        assertThat(r.outputs().get(0).stdout()).isEqualTo("5\n");
        assertThat(r.outputs().get(0).exitCode()).isZero();
        assertThat(r.outputs().get(0).timedOut()).isFalse();
    }

    @Test
    void wrongAnswerIsStillAnOutputBecauseTheComparisonIsInJava() {
        fake.script = s -> judgement(4, "Wrong Answer", "7\n", "", null, null);
        Output o = service().run("python", "print(7)", List.of("1")).outputs().get(0);

        assertThat(o.stdout()).isEqualTo("7\n");
        assertThat(o.exitCode()).isZero();
        assertThat(CodeExecutionService.sameOutput("5", o.stdout())).isFalse();
    }

    @Test
    void timeLimitExceededSetsTimedOut() {
        fake.script = s -> judgement(5, "Time Limit Exceeded", "", "", null, null);
        Output o = service().run("python", "while True: pass", List.of("1")).outputs().get(0);

        assertThat(o.timedOut()).isTrue();
        assertThat(o.exitCode()).isEqualTo(124);
    }

    @Test
    void compileErrorIsReturnedAsCompileErrorWithNoOutputs() {
        fake.script = s -> judgement(6, "Compilation Error", "", "", "Main.java:3: error: ';' expected", null);
        Result r = service().run("java", "class Main {", List.of("1", "2"));

        assertThat(r.compileError()).contains("';' expected");
        assertThat(r.outputs()).isEmpty();
    }

    @Test
    void runtimeErrorIsAFailedCaseWithAReadableError() {
        fake.script = s -> judgement(11, "Runtime Error (Other)", "", "", null, null);
        Output o = service().run("python", "1/0", List.of("1")).outputs().get(0);

        assertThat(o.exitCode()).isNotZero();
        assertThat(o.stderr()).isEqualTo("Runtime Error (Other)"); // no stderr from the program: the judge's status text is shown
        assertThat(o.timedOut()).isFalse();
    }

    @Test
    void internalErrorIsASystemErrorNotAStudentFailure() {
        fake.script = s -> judgement(13, "Internal Error", "", "", null, "isolate: cgroup not writable");
        assertThatThrownBy(() -> service().run("python", "print(1)", List.of("1")))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.INTERNAL));
    }

    @Test
    void execFormatErrorIsAlsoASystemError() {
        fake.script = s -> judgement(14, "Exec Format Error", "", "", null, null);
        assertThatThrownBy(() -> service().run("python", "print(1)", List.of("1")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void badTokenSurfacesAsAuthErrorToTheCaller() {
        fake.httpStatus = 401;
        assertThatThrownBy(() -> service().run("python", "print(1)", List.of("1")))
                .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.AUTH));
    }

    @Test
    void configuredLanguageIdIsSentToJudge0() {
        CodeExecutionProperties.Judge0 j = new CodeExecutionProperties.Judge0(fake.url(), "", "X-Auth-Token", 20L, 5_000L, 4,
                1000, 5_000, 3.0, 8.0, 262_144L, 10.0, 15.0, 524_288L, 20);
        CodeExecutionProperties props = new CodeExecutionProperties("judge0", 0, 2_000L, 65_536, 65_536, 65_536, j,
                java.util.Map.of("python", 999, "java", 62, "cpp", 54, "javascript", 63));
        fake.script = s -> judgement(3, "Accepted", "", "", null, null);
        new Judge0CodeExecutionService(new Judge0Client(props), props).run("python", "print(1)", List.of("1"));

        assertThat(fake.posts().get(0).body()).contains("\"language_id\":999");
    }

    @Test
    void cpuLimitIsCappedEvenIfConfiguredHigher() {
        CodeExecutionProperties.Judge0 j = new CodeExecutionProperties.Judge0(fake.url(), "", "X-Auth-Token", 20L, 5_000L, 4,
                1000, 5_000, 60.0, 8.0, 262_144L, 10.0, 15.0, 524_288L, 20);
        CodeExecutionProperties props = new CodeExecutionProperties("judge0", 0, 2_000L, 65_536, 65_536, 65_536, j, null);
        fake.script = s -> judgement(3, "Accepted", "", "", null, null);
        new Judge0CodeExecutionService(new Judge0Client(props), props).run("python", "print(1)", List.of("1"));

        assertThat(fake.posts().get(0).body()).contains("\"cpu_time_limit\":10.0");
    }

    @Test
    void unsupportedLanguageIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> service().run("ruby", "puts 1", List.of("1")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo("LANGUAGE_UNSUPPORTED"));
        // "c" is configured, but the platform does not offer it yet (PrepModel.LANGUAGES), so it is still refused
        assertThatThrownBy(() -> service().run("c", "int main(){}", List.of("1")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo("LANGUAGE_UNSUPPORTED"));
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void emptyAndOversizedSourceAreRejected() {
        assertThatThrownBy(() -> service().run("python", "   ", List.of("1")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo("EMPTY_CODE"));
        String big = "#".repeat(65_537);
        assertThatThrownBy(() -> service().run("python", big, List.of("1")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo("CODE_TOO_LONG"));
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void oversizedStdinIsRejected() {
        String big = "1".repeat(1_048_577);
        assertThatThrownBy(() -> service().run("python", "print(1)", List.of(big)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo("INPUT_TOO_LARGE"));
    }

    @Test
    void whenAllSlotsAreBusyTheRunFailsWithJudgeBusy() throws Exception {
        CountDownLatch inJudge = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fake.script = s -> {
            inJudge.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return judgement(3, "Accepted", "", "", null, null);
        };
        Judge0CodeExecutionService svc = service(1, 200, 3.0);
        AtomicReference<Throwable> first = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                svc.run("python", "print(1)", List.of("1"));
            } catch (Throwable e) {
                first.set(e);
            }
        });
        t.start();
        try {
            assertThat(inJudge.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> svc.run("python", "print(2)", List.of("1")))
                    .isInstanceOfSatisfying(CodeRunnerUnavailableException.class,
                            e -> assertThat(e.getReason()).isEqualTo(CodeRunnerUnavailableException.Reason.BUSY));
        } finally {
            release.countDown();
            t.join(10_000);
        }
        assertThat(first.get()).isNull();
    }
}
