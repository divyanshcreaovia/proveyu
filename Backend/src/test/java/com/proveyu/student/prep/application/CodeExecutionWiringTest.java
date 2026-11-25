package com.proveyu.student.prep.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Which runner is wired for which provider, and that the Judge0 settings bind from configuration (no database needed). */
class CodeExecutionWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Judge0Client.class, Judge0CodeExecutionService.class, DockerCodeRunner.class,
                    CodeRunRateLimiter.class, CodeRunnerHealthService.class);

    @Test
    void judge0IsTheDefaultProvider() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(CodeExecutionService.class);
            assertThat(ctx).hasSingleBean(Judge0CodeExecutionService.class);
            assertThat(ctx).doesNotHaveBean(DockerCodeRunner.class);
            assertThat(ctx.getBean(CodeExecutionService.class)).isInstanceOf(Judge0CodeExecutionService.class);
        });
    }

    @Test
    void dockerProviderIsSelectableWithoutJudge0() {
        runner.withPropertyValues("code.execution.provider=docker").run(ctx -> {
            assertThat(ctx).hasSingleBean(CodeExecutionService.class);
            assertThat(ctx).hasSingleBean(DockerCodeRunner.class);
            assertThat(ctx).doesNotHaveBean(Judge0CodeExecutionService.class);
        });
    }

    @Test
    void judge0SettingsBindFromEnvStyleProperties() {
        runner.withPropertyValues(
                "code.execution.judge0.url=http://10.128.0.5:2358",
                "code.execution.judge0.token=abc123",
                "code.execution.judge0.auth-header=X-Auth-Token",
                "code.execution.judge0.max-parallel=7",
                "code.execution.judge0.poll-timeout-ms=12000",
                "code.execution.rate-limit-seconds=3").run(ctx -> {
            CodeExecutionProperties p = ctx.getBean(CodeExecutionProperties.class);
            assertThat(p.judge0().url()).isEqualTo("http://10.128.0.5:2358");
            assertThat(p.judge0().token()).isEqualTo("abc123");
            assertThat(p.judge0().maxParallel()).isEqualTo(7);
            assertThat(p.judge0().pollTimeoutMs()).isEqualTo(12_000L);
            assertThat(p.rateLimitSeconds()).isEqualTo(3);
        });
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        runner.run(ctx -> {
            CodeExecutionProperties p = ctx.getBean(CodeExecutionProperties.class);
            assertThat(p.provider()).isEqualTo("judge0");
            assertThat(p.judge0().url()).isEqualTo("http://localhost:2358");
            assertThat(p.judge0().authHeader()).isEqualTo("X-Auth-Token");
            assertThat(p.judge0().token()).isEmpty();
            assertThat(p.judge0().pollIntervalMs()).isEqualTo(300L);
            assertThat(p.judge0().pollTimeoutMs()).isEqualTo(30_000L);
            assertThat(p.judge0().connectTimeoutMs()).isEqualTo(3000);
            assertThat(p.judge0().readTimeoutMs()).isEqualTo(15_000);
            assertThat(p.judge0().maxParallel()).isEqualTo(4);
            assertThat(p.rateLimitSeconds()).isEqualTo(5);
            assertThat(p.languages()).containsEntry("python", 71).containsEntry("c", 50).containsEntry("cpp", 54)
                    .containsEntry("java", 62).containsEntry("javascript", 63);
        });
    }

    @Test
    void limitsAreClampedToTheHardCaps() {
        runner.withPropertyValues("code.execution.judge0.cpu-time-limit-seconds=120", "code.execution.judge0.memory-limit-kb=9999999")
                .run(ctx -> {
                    CodeExecutionProperties.Judge0 j = ctx.getBean(CodeExecutionProperties.class).judge0();
                    assertThat(j.cpuLimit()).isEqualTo(10.0);
                    assertThat(j.memoryLimit()).isEqualTo(524_288L);
                });
    }
}
