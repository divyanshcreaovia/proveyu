package com.proveyu.student.prep.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Judge0VerdictTest {

    @Test
    void statusIdsMapToVerdicts() {
        assertThat(Judge0Verdict.of(1)).isEqualTo(Judge0Verdict.IN_QUEUE);
        assertThat(Judge0Verdict.of(2)).isEqualTo(Judge0Verdict.PROCESSING);
        assertThat(Judge0Verdict.of(3)).isEqualTo(Judge0Verdict.ACCEPTED);
        assertThat(Judge0Verdict.of(4)).isEqualTo(Judge0Verdict.WRONG_ANSWER);
        assertThat(Judge0Verdict.of(5)).isEqualTo(Judge0Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(Judge0Verdict.of(6)).isEqualTo(Judge0Verdict.COMPILATION_ERROR);
        for (int id = 7; id <= 12; id++) assertThat(Judge0Verdict.of(id)).as("status %d", id).isEqualTo(Judge0Verdict.RUNTIME_ERROR);
        assertThat(Judge0Verdict.of(13)).isEqualTo(Judge0Verdict.INTERNAL_ERROR);
        assertThat(Judge0Verdict.of(14)).isEqualTo(Judge0Verdict.EXEC_FORMAT_ERROR);
    }

    @Test
    void unknownIdsAreUnknownNotAccepted() {
        assertThat(Judge0Verdict.of(0)).isEqualTo(Judge0Verdict.UNKNOWN);
        assertThat(Judge0Verdict.of(99)).isEqualTo(Judge0Verdict.UNKNOWN);
    }

    @Test
    void onlyIdsAboveTwoAreFinished() {
        assertThat(Judge0Verdict.isFinished(1)).isFalse();
        assertThat(Judge0Verdict.isFinished(2)).isFalse();
        assertThat(Judge0Verdict.isFinished(3)).isTrue();
        assertThat(Judge0Verdict.isFinished(14)).isTrue();
    }

    @Test
    void outputComparisonIgnoresTrailingWhitespaceOnly() {
        assertThat(CodeExecutionService.sameOutput("5\n", "5")).isTrue();
        assertThat(CodeExecutionService.sameOutput("1 2\r\n3  \n\n", "1 2\n3")).isTrue();
        assertThat(CodeExecutionService.sameOutput("1 2", "1  2")).isFalse();
        assertThat(CodeExecutionService.sameOutput("5", "6")).isFalse();
        assertThat(CodeExecutionService.sameOutput(null, "")).isTrue();
    }
}
