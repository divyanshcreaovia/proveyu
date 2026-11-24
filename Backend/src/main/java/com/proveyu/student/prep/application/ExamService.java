package com.proveyu.student.prep.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.college.domain.CollegeBranch;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import com.proveyu.student.prep.domain.PrepAttempt;
import com.proveyu.student.prep.domain.PrepModel.*;
import com.proveyu.student.prep.domain.PrepTest;
import com.proveyu.student.prep.infrastructure.PrepAttemptRepository;
import com.proveyu.student.prep.infrastructure.PrepTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.proveyu.student.prep.domain.PrepModel.TEXT;

/**
 * College exams (sessionals) run on the college's own tests: exam settings (window, branches / batches, pool and shuffling, what
 * students see), the live invigilator view with extra time, marking descriptive answers, and the marks register as CSV.
 * Coordinators only see and act on students of their branches.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final int MAX_EXTRA_MINUTES = 120;

    private final PrepService prep;
    private final PrepTestRepository tests;
    private final PrepAttemptRepository attempts;
    private final CollegeStudentRepository students;
    private final CollegeDirectory directory;
    private final ObjectMapper json;
    private final TestNotifier notifier;

    // ================================================================== settings

    @Transactional
    public AdminTestSummary saveSettings(CollegeContext ctx, UUID testId, ExamSettings r) {
        PrepTest t = prep.collegeTest(ctx.collegeId(), testId);
        String wasMode = t.getMode();
        Instant wasStart = t.getStartsAt(), wasEnd = t.getEndsAt();
        boolean wasReleased = t.isResultsReleased();
        String wasBranches = t.getBranchIds(), wasYears = t.getBatchYears();
        String mode = r.mode() == null ? "PRACTICE" : r.mode().trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("PRACTICE") && !mode.equals("EXAM")) throw bad("mode must be PRACTICE or EXAM", "INVALID_MODE");
        boolean hasAttempts = !attempts.findAllByTestIdOrderByStartedAtDesc(testId).isEmpty();
        if (hasAttempts && !mode.equals(t.getMode())) {
            throw new DomainException("Students already took this test: create a new test to change between practice and exam", HttpStatus.CONFLICT, "TEST_HAS_ATTEMPTS");
        }
        if (hasAttempts && (r.shuffleQuestions() != t.isShuffleQuestions() || r.shuffleOptions() != t.isShuffleOptions())) {
            throw new DomainException("Shuffling cannot change after students have started", HttpStatus.CONFLICT, "TEST_HAS_ATTEMPTS");
        }
        if (mode.equals("EXAM")) {
            if (r.startsAt() == null || r.endsAt() == null) throw bad("An exam needs a start and an end time", "EXAM_WINDOW_REQUIRED");
            if (!r.endsAt().isAfter(r.startsAt().plus(Duration.ofMinutes(5)))) throw bad("The exam must end at least 5 minutes after it starts", "EXAM_WINDOW_INVALID");
            if (Duration.between(r.startsAt(), r.endsAt()).toDays() > 30) throw bad("The exam window can be at most 30 days", "EXAM_WINDOW_INVALID");
            Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
            List<UUID> chosen = r.branchIds() == null ? List.of() : r.branchIds();
            for (UUID b : chosen) {
                if (!branches.containsKey(b)) throw bad("Unknown branch " + b, "INVALID_BRANCH");
                if (!ctx.canAccessBranch(b)) throw new DomainException("You can only target your own branches", HttpStatus.FORBIDDEN, "BRANCH_SCOPE");
            }
            if (chosen.isEmpty() && !ctx.canAccessBranch(null)) throw new DomainException("Choose your branches for this exam", HttpStatus.FORBIDDEN, "BRANCH_SCOPE");
            List<Integer> years = r.batchYears() == null ? List.of() : r.batchYears();
            for (Integer y : years) if (y == null || y < 2000 || y > 2100) throw bad("Batch year must be 2000-2100", "INVALID_BATCH");
            t.setStartsAt(r.startsAt());
            t.setEndsAt(r.endsAt());
            t.setBranchIds(chosen.isEmpty() ? null : chosen.stream().map(UUID::toString).distinct().collect(Collectors.joining(",")));
            t.setBatchYears(years.isEmpty() ? null : years.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));
            t.setResultsReleased(r.resultsReleased());
        } else {
            t.setStartsAt(null);
            t.setEndsAt(null);
            t.setBranchIds(null);
            t.setBatchYears(null);
            t.setResultsReleased(true);
        }
        t.setMode(mode);
        t.setShuffleQuestions(r.shuffleQuestions());
        t.setShuffleOptions(r.shuffleOptions());
        t.setShowAnswers(r.showAnswers());
        boolean windowChanged = !Objects.equals(wasStart, t.getStartsAt()) || !Objects.equals(wasEnd, t.getEndsAt());
        if (windowChanged) t.setLiveNotifiedAt(null); // the scheduler announces the new start
        tests.save(t);
        if (t.isPublished()) { // tell the students what changed
            boolean audienceChanged = !Objects.equals(wasBranches, t.getBranchIds()) || !Objects.equals(wasYears, t.getBatchYears());
            if (!mode.equals(wasMode) || (t.isExam() && audienceChanged)) notifier.published(t);
            else if (t.isExam() && windowChanged) notifier.rescheduled(t);
            if (t.isExam() && t.isResultsReleased() && !wasReleased) notifier.resultsReleased(t);
        }
        return prep.collegeList(ctx.collegeId()).stream().filter(x -> x.id().equals(testId)).findFirst().orElseThrow();
    }

    // ================================================================== live monitor

    @Transactional(readOnly = true)
    public ExamMonitor monitor(CollegeContext ctx, UUID testId) {
        PrepTest t = prep.collegeTest(ctx.collegeId(), testId);
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        List<CollegeStudent> eligible = students.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> s.getUserId() != null && ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> !t.isExam() || PrepService.eligible(t, s))
                .sorted(Comparator.comparing((CollegeStudent s) -> s.getRollNumber() == null ? "" : s.getRollNumber()))
                .toList();
        Map<UUID, PrepAttempt> latest = new HashMap<>();
        for (PrepAttempt a : attempts.findAllByTestIdOrderByStartedAtDesc(testId)) latest.putIfAbsent(a.getUserId(), a);
        Map<UUID, Integer> switches = prep.switchCounts(latest.values().stream().map(PrepAttempt::getId).toList());
        Instant now = Instant.now();
        List<MonitorRow> rows = new ArrayList<>();
        int notStarted = 0, inProgress = 0, submitted = 0, pending = 0;
        for (CollegeStudent s : eligible) {
            PrepAttempt a = latest.get(s.getUserId());
            CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
            String status = a == null ? "NOT_STARTED" : a.getSubmittedAt() != null ? "SUBMITTED" : now.isAfter(a.getDeadlineAt()) ? "TIME_UP" : "IN_PROGRESS";
            switch (status) {
                case "NOT_STARTED" -> notStarted++;
                case "SUBMITTED" -> submitted++;
                default -> inProgress++;
            }
            if (a != null && "PENDING".equals(a.getReviewStatus())) pending++;
            int questions = a == null ? t.getQuestionCount() : questionCount(t, a);
            rows.add(new MonitorRow(s.getId(), CollegeDirectory.fullName(s), s.getRollNumber(), b == null ? null : b.getName(), s.getBatchYear(), status,
                    a == null ? null : a.getId(), a == null ? null : a.getStartedAt(), a == null ? null : a.getDeadlineAt(),
                    a == null ? null : a.getSubmittedAt(), a == null ? null : a.getUpdatedAt(), a == null ? 0 : answered(a), questions,
                    a == null ? null : a.getScore(), a == null ? t.getTotalMarks() : a.getMaxScore(),
                    a == null ? 0 : switches.getOrDefault(a.getId(), 0), a == null ? 0 : a.getAiWarningCount(),
                    a == null ? null : a.getAutoSubmitReason(), a == null ? null : a.getReviewStatus(), a == null ? 0 : a.getExtraMinutes()));
        }
        return new ExamMonitor(t.getId(), t.getTitle(), t.getMode(), t.getStartsAt(), t.getEndsAt(), now, eligible.size(), notStarted, inProgress,
                submitted, pending, rows);
    }

    /** Gives one student more time on a running attempt (also past the exam window). */
    @Transactional
    public MonitorRow extend(CollegeContext ctx, UUID testId, UUID attemptId, int minutes) {
        if (minutes < 1 || minutes > MAX_EXTRA_MINUTES) throw bad("Extra time must be 1-" + MAX_EXTRA_MINUTES + " minutes", "INVALID_MINUTES");
        PrepTest t = prep.collegeTest(ctx.collegeId(), testId);
        PrepAttempt a = attempts.lockById(attemptId).filter(x -> x.getTestId().equals(t.getId())).orElseThrow(() -> notFound("Attempt"));
        studentOf(ctx, a.getUserId());
        if (a.getSubmittedAt() != null) throw new DomainException("This attempt is already submitted", HttpStatus.CONFLICT, "ATTEMPT_SUBMITTED");
        Instant base = a.getDeadlineAt().isBefore(Instant.now()) ? Instant.now() : a.getDeadlineAt(); // time already up: count from now
        a.setDeadlineAt(base.plus(Duration.ofMinutes(minutes)));
        a.setExtraMinutes(a.getExtraMinutes() + minutes);
        attempts.save(a);
        notifier.extraTime(t, a.getUserId(), minutes);
        return monitor(ctx, testId).rows().stream().filter(r -> attemptId.equals(r.attemptId())).findFirst().orElseThrow(() -> notFound("Attempt"));
    }

    // ================================================================== marking descriptive answers

    @Transactional(readOnly = true)
    public ReviewView review(CollegeContext ctx, UUID testId, UUID attemptId) {
        PrepAttempt a = submittedAttempt(ctx, testId, attemptId);
        CollegeStudent s = studentOf(ctx, a.getUserId());
        return new ReviewView(a.getId(), CollegeDirectory.fullName(s), s.getRollNumber(), prep.storedResult(a));
    }

    /** Marks for descriptive (TEXT) questions: 0..question marks each; the score is recomputed. */
    @Transactional
    public ReviewView mark(CollegeContext ctx, UUID testId, UUID attemptId, Map<String, BigDecimal> marks) {
        if (marks == null || marks.isEmpty()) throw bad("Give marks for at least one question", "MARKS_REQUIRED");
        PrepAttempt a = submittedAttempt(ctx, testId, attemptId);
        AttemptResult r = prep.storedResult(a);
        Map<String, QuestionResult> byId = r.questions().stream().collect(Collectors.toMap(QuestionResult::id, Function.identity()));
        for (Map.Entry<String, BigDecimal> m : marks.entrySet()) {
            QuestionResult q = byId.get(m.getKey());
            if (q == null || !TEXT.equals(q.type())) throw bad("Question " + m.getKey() + " is not a descriptive question of this attempt", "NOT_TEXT_QUESTION");
            BigDecimal v = m.getValue();
            if (v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(q.marks())) > 0) {
                throw bad("Marks for " + m.getKey() + " must be 0-" + q.marks(), "INVALID_MARKS");
            }
        }
        List<QuestionResult> qs = new ArrayList<>();
        Map<String, BigDecimal> secScore = new LinkedHashMap<>();
        Map<String, int[]> secStats = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean pending = false;
        for (QuestionResult q : r.questions()) {
            BigDecimal v = marks.get(q.id());
            if (v != null) {
                q = new QuestionResult(q.id(), q.type(), q.section(), q.title(), q.text(), q.options(), q.yourOption(), q.correctOption(),
                        q.explanation(), q.language(), q.code(), q.testsPassed(), q.testsTotal(), q.compileError(), v.setScale(2, RoundingMode.HALF_UP),
                        q.marks(), q.answerText(), false);
            }
            if (Boolean.TRUE.equals(q.pendingReview())) pending = true;
            qs.add(q);
            total = total.add(q.marksAwarded());
            secScore.merge(q.section(), q.marksAwarded(), BigDecimal::add);
            int[] st = secStats.computeIfAbsent(q.section(), k -> new int[3]);
            st[0] += q.marks();
            st[1] += q.marksAwarded().compareTo(BigDecimal.valueOf(q.marks())) == 0 ? 1 : 0;
            st[2] += 1;
        }
        List<SectionScore> sections = secStats.entrySet().stream()
                .map(e -> new SectionScore(e.getKey(), secScore.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        int percent = r.maxScore() == 0 ? 0 : total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(r.maxScore()), 0, RoundingMode.HALF_UP).intValue();
        AttemptResult updated = new AttemptResult(r.attemptId(), r.testId(), r.title(), r.companyTag(), r.startedAt(), r.submittedAt(),
                r.timeTakenSeconds(), total, r.maxScore(), percent, sections, qs, true, true, pending);
        try {
            a.setResultJson(json.writeValueAsString(updated));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        a.setScore(total);
        a.setReviewStatus(pending ? "PENDING" : "DONE");
        attempts.save(a);
        notifier.marked(prep.collegeTest(ctx.collegeId(), testId), a.getUserId());
        return review(ctx, testId, attemptId);
    }

    // ================================================================== marks register

    /** One row per eligible student (also those who did not take it), for the internal marks register. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(CollegeContext ctx, UUID testId) {
        ExamMonitor m = monitor(ctx, testId);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
        StringBuilder out = new StringBuilder("﻿"); // Excel opens UTF-8 with the BOM
        out.append("Roll number,Name,Branch,Batch,Status,Score,Max marks,Percent,Started,Submitted,Tab switches,Camera/mic events,Auto-submitted,Review\r\n");
        for (MonitorRow r : m.rows()) {
            String pct = r.score() == null || r.maxScore() == 0 ? "" : r.score().multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(r.maxScore()), 1, RoundingMode.HALF_UP).toPlainString();
            out.append(String.join(",", cell(r.rollNumber()), cell(r.studentName()), cell(r.branchName()), cell(r.batchYear() == null ? "" : r.batchYear().toString()),
                    cell(r.status().equals("NOT_STARTED") ? "ABSENT" : r.status()), cell(r.score() == null ? "" : r.score().stripTrailingZeros().toPlainString()),
                    String.valueOf(r.maxScore()), pct, cell(r.startedAt() == null ? "" : f.format(r.startedAt())),
                    cell(r.submittedAt() == null ? "" : f.format(r.submittedAt())), String.valueOf(r.tabSwitches()), String.valueOf(r.aiWarnings()),
                    cell(r.autoSubmitReason() == null ? "" : r.autoSubmitReason()), cell(r.reviewStatus() == null ? "" : r.reviewStatus()))).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ================================================================== helpers

    private PrepAttempt submittedAttempt(CollegeContext ctx, UUID testId, UUID attemptId) {
        PrepTest t = prep.collegeTest(ctx.collegeId(), testId);
        PrepAttempt a = attempts.findById(attemptId).filter(x -> x.getTestId().equals(t.getId())).orElseThrow(() -> notFound("Attempt"));
        studentOf(ctx, a.getUserId());
        if (a.getSubmittedAt() == null || a.getResultJson() == null) throw new DomainException("This attempt is not submitted yet", HttpStatus.CONFLICT, "ATTEMPT_OPEN");
        return a;
    }

    /** The attempt's student, in this college and in the member's branches (404 otherwise, so ids cannot be probed). */
    private CollegeStudent studentOf(CollegeContext ctx, UUID userId) {
        CollegeStudent s = students.findFirstByUserIdAndDeletedAtIsNull(userId).orElseThrow(() -> notFound("Attempt"));
        if (!s.getCollegeId().equals(ctx.collegeId()) || !ctx.canAccessBranch(s.getBranchId())) throw notFound("Attempt");
        return s;
    }

    private int questionCount(PrepTest t, PrepAttempt a) {
        PrepService.Layout l = prep.layout(a);
        return l == null ? t.getQuestionCount() : l.questions().size();
    }

    /** Questions with an answer (an option, code or text). */
    private int answered(PrepAttempt a) {
        try {
            Map<String, Map<String, Object>> m = json.readValue(a.getAnswersJson() == null ? "{}" : a.getAnswersJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {});
            return (int) m.values().stream().filter(v -> v != null && (v.get("option") != null
                    || (v.get("code") instanceof String c && !c.isBlank()) || (v.get("text") instanceof String x && !x.isBlank()))).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /** CSV cell; a leading = + - @ is neutralised so a spreadsheet does not run it as a formula. */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static DomainException bad(String msg, String code) {
        return new DomainException(msg, HttpStatus.BAD_REQUEST, code);
    }

    private static DomainException notFound(String what) {
        return new DomainException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
