package com.proveyu.prep.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON shapes of the Preparation module: the uploaded test definition, what students see, and results. */
public final class PrepModel {
    private PrepModel() {}

    public static final String MCQ = "MCQ";
    public static final String CODING = "CODING";
    public static final List<String> LANGUAGES = List.of("python", "java", "cpp", "javascript");

    // ------------------------------------------------------------------ uploaded definition (admin)

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** level (EASY / MEDIUM / HARD) and order are optional: tests with an order form the unlock ladder. */
    public record TestDefinition(String title, String description, String companyTag, String level, Integer order, Integer durationMinutes,
                                 List<Section> sections) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String name, List<Question> questions) {}

    /** MCQ: options + answer (0-based). CODING: title, text, starterCode per language, visible samples and hidden tests. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(String id, String type, String title, String text, List<String> options, Integer answer, Integer marks,
                           String explanation, Map<String, String> starterCode, List<IoCase> samples, List<IoCase> tests) {
        public int marksOrDefault() {
            return marks != null && marks > 0 ? marks : (CODING.equals(type) ? 10 : 1);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IoCase(String input, String output) {}

    // ------------------------------------------------------------------ admin views

    public record AdminTestSummary(UUID id, String title, String description, String companyTag, int durationMinutes, int questionCount,
                                   int codingCount, int totalMarks, boolean published, long attempts, Instant createdAt, String level,
                                   Integer order, long students, Integer averagePercent) {}

    // ------------------------------------------------------------------ student views

    public record TestCard(UUID id, String title, String description, String companyTag, int durationMinutes, int questionCount,
                           int codingCount, int totalMarks, List<String> sections, int attempts, BigDecimal bestScore,
                           UUID inProgressAttemptId, UUID lastAttemptId, String level, Integer order, Integer bestPercent, boolean locked,
                           String unlockAfter, int unlockPercent, boolean passed) {}

    /** A question as the student sees it during the test: no answer, no hidden tests. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(String id, String type, String section, String title, String text, List<String> options, int marks,
                               Map<String, String> starterCode, List<IoCase> samples) {}

    public record AttemptView(UUID attemptId, UUID testId, String title, String companyTag, int durationMinutes, Instant startedAt,
                              Instant deadlineAt, Instant serverNow, List<String> languages, List<QuestionView> questions,
                              Map<String, Answer> answers) {}

    /** MCQ answer = option index; coding answer = language + code. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(Integer option, String language, String code) {}

    public record RunRequest(String questionId, String language, String code) {}

    public record CaseResult(int index, boolean passed, String input, String expected, String actual, String error, boolean timedOut) {}

    public record RunResponse(String compileError, List<CaseResult> cases) {}

    // ------------------------------------------------------------------ results

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionResult(String id, String type, String section, String title, String text, List<String> options, Integer yourOption,
                                 Integer correctOption, String explanation, String language, String code, Integer testsPassed,
                                 Integer testsTotal, String compileError, BigDecimal marksAwarded, int marks) {}

    public record SectionScore(String name, BigDecimal score, int max, int correct, int total) {}

    public record AttemptResult(UUID attemptId, UUID testId, String title, String companyTag, Instant startedAt, Instant submittedAt,
                                long timeTakenSeconds, BigDecimal score, int maxScore, int percent, List<SectionScore> sections,
                                List<QuestionResult> questions) {}

    public record AttemptHistory(UUID attemptId, UUID testId, String title, Instant startedAt, Instant submittedAt, BigDecimal score,
                                 int maxScore) {}
}
