package com.proveyu.student.prep.domain;

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
    public static final String TEXT = "TEXT";
    public static final List<String> LANGUAGES = List.of("python", "java", "cpp", "javascript");

    // ------------------------------------------------------------------ uploaded definition (admin)

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** level (EASY / MEDIUM / HARD) and order are optional: tests with an order form the unlock ladder. */
    public record TestDefinition(String title, String description, String companyTag, String level, Integer order, Integer durationMinutes,
                                 List<Section> sections) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** pick: give each student this many random questions of the section (a question pool); null = all of them. */
    public record Section(String name, Integer pick, List<Question> questions) {}

    /**
     * MCQ: options + answer (0-based). CODING: title, text, starterCode per language, visible samples and hidden tests.
     * TEXT: a descriptive answer marked by a teacher (explanation = model answer / marking notes for the teacher).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(String id, String type, String title, String text, List<String> options, Integer answer, Integer marks,
                           String explanation, Map<String, String> starterCode, List<IoCase> samples, List<IoCase> tests) {
        public int marksOrDefault() {
            return marks != null && marks > 0 ? marks : (CODING.equals(type) ? 10 : TEXT.equals(type) ? 5 : 1);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IoCase(String input, String output) {}

    // ------------------------------------------------------------------ admin views

    public record AdminTestSummary(UUID id, String title, String description, String companyTag, int durationMinutes, int questionCount,
                                   int codingCount, int totalMarks, boolean published, long attempts, Instant createdAt, String level,
                                   Integer order, long students, Integer averagePercent, ExamSettings exam) {}

    // ------------------------------------------------------------------ student views

    public record TestCard(UUID id, String title, String description, String companyTag, int durationMinutes, int questionCount,
                           int codingCount, int totalMarks, List<String> sections, int attempts, BigDecimal bestScore,
                           UUID inProgressAttemptId, UUID lastAttemptId, String level, Integer order, Integer bestPercent, boolean locked,
                           String unlockAfter, int unlockPercent, boolean passed, boolean aiProctoring,
                           String mode, Instant startsAt, Instant endsAt, String examStatus, boolean resultsReleased, boolean collegeTest) {}

    /** A question as the student sees it during the test: no answer, no hidden tests. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(String id, String type, String section, String title, String text, List<String> options, int marks,
                               Map<String, String> starterCode, List<IoCase> samples) {}

    /** tabSwitches: times the student already left this attempt; maxWarnings: switches allowed before the next one submits it. */
    public record AttemptView(UUID attemptId, UUID testId, String title, String companyTag, int durationMinutes, Instant startedAt,
                              Instant deadlineAt, Instant serverNow, List<String> languages, List<QuestionView> questions,
                              Map<String, Answer> answers, int tabSwitches, int maxWarnings,
                              Instant consentAt, boolean aiProctoring, int aiWarnings, int maxAiWarnings, String mode) {}

    // ---------------------------------------------------------------- proctoring (leaving the test)
    /** One time the student left the test. seq 1..maxWarnings were warnings; seq maxWarnings + 1 submitted the test. */
    public record ProctorEventView(UUID id, String kind, int seq, Instant occurredAt, boolean hasScreenshot, boolean ai, String metadata) {}

    /** Reply to a reported switch. autoSubmitted: this switch went over the limit and the test is now submitted. */
    public record ProctorStatus(int tabSwitches, int maxWarnings, int warningsLeft, boolean autoSubmitted, ProctorEventView event,
                                int aiWarnings, int maxAiWarnings, boolean flagged, boolean recorded) {}

    /** All switches of one attempt, for the result page and the placement cell. */
    public record ProctorLog(UUID attemptId, String autoSubmitReason, int maxWarnings, List<ProctorEventView> events,
                             int aiWarnings, int maxAiWarnings, boolean flagged) {}

    /** AI proctoring: flagged = more AI events than allowed (for human review; the test is never submitted for this). */
    public record ConsentView(UUID attemptId, Instant consentAt) {}

    /** One attempt with switches, across all colleges, for the platform admin. */
    public record AdminAttemptRow(UUID attemptId, String collegeName, String studentName, String rollNumber, String testTitle, Instant startedAt,
                                  Instant submittedAt, BigDecimal score, int maxScore, int tabSwitches, String autoSubmitReason,
                                  int aiWarnings) {}

    /** One attempt by a student of the college, for the placement cell. */
    public record CollegeAttemptRow(UUID attemptId, UUID studentId, String studentName, String rollNumber, String branchName,
                                    String testTitle, Instant startedAt, Instant submittedAt, BigDecimal score, int maxScore,
                                    int tabSwitches, String autoSubmitReason, int aiWarnings, UUID testId) {}

    /** MCQ answer = option index; coding answer = language + code. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(Integer option, String language, String code, String text) {}

    public record RunRequest(String questionId, String language, String code) {}

    /** Code ground: run any code with one stdin, no test, no score. */
    public record PlaygroundRequest(String language, String code, String stdin) {}

    /** compileError set = did not compile; otherwise what the program printed and how it ended. */
    public record PlaygroundResult(String compileError, String stdout, String stderr, Integer exitCode, boolean timedOut) {}

    public record CaseResult(int index, boolean passed, String input, String expected, String actual, String error, boolean timedOut) {}

    public record RunResponse(String compileError, List<CaseResult> cases) {}

    // ------------------------------------------------------------------ results

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionResult(String id, String type, String section, String title, String text, List<String> options, Integer yourOption,
                                 Integer correctOption, String explanation, String language, String code, Integer testsPassed,
                                 Integer testsTotal, String compileError, BigDecimal marksAwarded, int marks, String answerText,
                                 Boolean pendingReview) {}

    public record SectionScore(String name, BigDecimal score, int max, int correct, int total) {}

    public record AttemptResult(UUID attemptId, UUID testId, String title, String companyTag, Instant startedAt, Instant submittedAt,
                                long timeTakenSeconds, BigDecimal score, int maxScore, int percent, List<SectionScore> sections,
                                List<QuestionResult> questions, boolean released, boolean answersShown, boolean pendingReview) {}

    public record AttemptHistory(UUID attemptId, UUID testId, String title, Instant startedAt, Instant submittedAt, BigDecimal score,
                                 int maxScore, int tabSwitches, String autoSubmitReason, int aiWarnings, boolean released) {}

    // ------------------------------------------------------------------ college exams

    /** What a college sets on one of its tests to run it as an exam (mode PRACTICE ignores the rest). */
    public record ExamSettings(String mode, Instant startsAt, Instant endsAt, List<UUID> branchIds, List<Integer> batchYears,
                               boolean shuffleQuestions, boolean shuffleOptions, boolean showAnswers, boolean resultsReleased) {}

    /** One eligible student in the live monitor: NOT_STARTED, IN_PROGRESS, TIME_UP (not yet graded) or SUBMITTED. */
    public record MonitorRow(UUID studentId, String studentName, String rollNumber, String branchName, Integer batchYear, String status,
                             UUID attemptId, Instant startedAt, Instant deadlineAt, Instant submittedAt, Instant lastActivityAt,
                             int answered, int questions, BigDecimal score, int maxScore, int tabSwitches, int aiWarnings,
                             String autoSubmitReason, String reviewStatus, int extraMinutes) {}

    public record ExamMonitor(UUID testId, String title, String mode, Instant startsAt, Instant endsAt, Instant serverNow, int eligible,
                              int notStarted, int inProgress, int submitted, int pendingReview, List<MonitorRow> rows) {}

    /** A submitted attempt with every answer, for the teacher (descriptive answers to mark). */
    public record ReviewView(UUID attemptId, String studentName, String rollNumber, AttemptResult result) {}
}
