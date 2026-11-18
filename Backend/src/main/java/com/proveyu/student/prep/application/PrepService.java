package com.proveyu.student.prep.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.student.prep.domain.PrepAttempt;
import com.proveyu.student.prep.domain.PrepTest;
import com.proveyu.student.prep.domain.PrepModel;
import com.proveyu.student.prep.domain.PrepModel.*;
import com.proveyu.student.prep.infrastructure.PrepAttemptRepository;
import com.proveyu.student.prep.infrastructure.PrepTestRepository;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.proveyu.student.prep.domain.PrepModel.CODING;
import static com.proveyu.student.prep.domain.PrepModel.MCQ;

/** Mock tests: admin uploads JSON definitions; students take timed attempts that are scored on the server. */
@Service
@RequiredArgsConstructor
public class PrepService {

    /** Answers arriving this long after the deadline are still accepted (network delay on the final autosave/submit). */
    private static final Duration GRACE = Duration.ofSeconds(30);

    private final PrepTestRepository testRepository;
    private final PrepAttemptRepository attemptRepository;
    private final DockerCodeRunner runner;
    private final ObjectMapper json;
    private final com.proveyu.college.infrastructure.CollegeStudentRepository students;

    /** A ladder test opens once the best score in the test before it is MORE than this percentage. */
    @org.springframework.beans.factory.annotation.Value("${prep.unlock-percent:50}")
    private int unlockPercent = 50;

    // ================================================================== admin

    @Transactional
    public AdminTestSummary upload(UUID adminId, String rawJson) {
        return create(rawJson, adminId, null);
    }

    /** Uploaded by a college's placement team: only its students see it, and it is never part of the unlock path. */
    private AdminTestSummary create(String rawJson, UUID createdBy, UUID collegeId) {
        TestDefinition def = parse(rawJson);
        TestDefinition clean = validate(def);
        int q = 0, coding = 0, marks = 0;
        for (Section s : clean.sections()) for (Question x : s.questions()) {
            q++;
            if (CODING.equals(x.type())) coding++;
            marks += x.marksOrDefault();
        }
        PrepTest t = testRepository.save(PrepTest.builder().title(clean.title().trim()).description(clean.description())
                .collegeId(collegeId).companyTag(blankToNull(clean.companyTag())).level(clean.level())
                .levelOrder(collegeId == null ? clean.order() : null).durationMinutes(clean.durationMinutes()).questionCount(q).codingCount(coding)
                .totalMarks(marks).definitionJson(write(clean)).published(false).createdBy(createdBy).build());
        return summary(t, 0);
    }

    @Transactional(readOnly = true)
    public List<AdminTestSummary> adminList() {
        Map<UUID, Long> counts = new HashMap<>();
        attemptRepository.countByTest().forEach(r -> counts.put((UUID) r[0], (Long) r[1]));
        return testRepository.findAllByCollegeIdIsNullOrderByCreatedAtDesc().stream().map(t -> summary(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    // ================================================================== college placement team (TPO)

    public List<AdminTestSummary> collegeList(UUID collegeId) {
        List<PrepTest> tests = testRepository.findAllByCollegeIdOrderByCreatedAtDesc(collegeId);
        Map<UUID, Object[]> stats = new HashMap<>();
        if (!tests.isEmpty()) attemptRepository.statsByTest(tests.stream().map(PrepTest::getId).toList()).forEach(r -> stats.put((UUID) r[0], r));
        return tests.stream().map(t -> {
            Object[] r = stats.get(t.getId());
            if (r == null) return summary(t, 0);
            return summary(t, (Long) r[1], (Long) r[2], r[3] == null ? null : (int) Math.round(((Number) r[3]).doubleValue()));
        }).toList();
    }

    @Transactional
    public AdminTestSummary collegeUpload(UUID collegeId, UUID userId, String rawJson) {
        return create(rawJson, userId, collegeId);
    }

    @Transactional
    public AdminTestSummary collegeSetPublished(UUID collegeId, UUID id, boolean published) {
        PrepTest t = collegeTest(collegeId, id);
        t.setPublished(published);
        return summary(testRepository.save(t), 0);
    }

    @Transactional
    public PrepTest collegeDelete(UUID collegeId, UUID id) {
        PrepTest t = collegeTest(collegeId, id);
        testRepository.delete(t);
        return t;
    }

    public PrepTest collegeTest(UUID collegeId, UUID id) {
        return testRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Test"));
    }

    @Transactional
    public AdminTestSummary setPublished(UUID id, boolean published) {
        PrepTest t = test(id);
        t.setPublished(published);
        return summary(testRepository.save(t), 0);
    }

    @Transactional
    public void delete(UUID id) {
        testRepository.delete(test(id));
    }

    @Transactional(readOnly = true)
    public String definition(UUID id) {
        return test(id).getDefinitionJson();
    }

    // ================================================================== student

    @Transactional
    public List<TestCard> studentTests(UUID userId) {
        Map<UUID, List<PrepAttempt>> mine = attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .peek(this::autoSubmitIfExpired)
                .collect(Collectors.groupingBy(PrepAttempt::getTestId, LinkedHashMap::new, Collectors.toList()));
        UUID college = collegeOf(userId);
        List<PrepTest> tests = new ArrayList<>(testRepository.findAllByPublishedTrueOrderByCreatedAtDesc().stream()
                .filter(t -> visibleTo(t, college)).toList());
        // Ladder tests first, easiest to hardest; then the standalone tests, newest first.
        tests.sort(Comparator.comparing((PrepTest t) -> t.getLevelOrder() == null ? Integer.MAX_VALUE : t.getLevelOrder()));
        Map<UUID, String> lockedBy = lockedTests(tests, mine);
        return tests.stream().map(t -> {
            List<PrepAttempt> a = mine.getOrDefault(t.getId(), List.of());
            BigDecimal best = a.stream().map(PrepAttempt::getScore).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            UUID open = a.stream().filter(x -> x.getSubmittedAt() == null).map(PrepAttempt::getId).findFirst().orElse(null);
            UUID last = a.stream().filter(x -> x.getSubmittedAt() != null).map(PrepAttempt::getId).findFirst().orElse(null);
            List<String> sections = definition(t).sections().stream().map(Section::name).toList();
            return new TestCard(t.getId(), t.getTitle(), t.getDescription(), t.getCompanyTag(), t.getDurationMinutes(), t.getQuestionCount(),
                    t.getCodingCount(), t.getTotalMarks(), sections, (int) a.stream().filter(x -> x.getSubmittedAt() != null).count(), best, open, last,
                    t.getLevel(), t.getLevelOrder(), bestPercent(a), lockedBy.containsKey(t.getId()) && open == null, lockedBy.get(t.getId()), unlockPercent, passed(a));
        }).toList();
    }

    /**
     * Ladder tests the student cannot start yet, mapped to the title of the test they must pass first. The first ladder test is
     * always open; test k opens when the best score in test k-1 is more than {@link #unlockPercent}.
     */
    private Map<UUID, String> lockedTests(List<PrepTest> published, Map<UUID, List<PrepAttempt>> mine) {
        Map<UUID, String> locked = new HashMap<>();
        PrepTest previous = null;
        for (PrepTest t : published) {
            if (t.getLevelOrder() == null) continue;
            if (previous != null) {
                if (locked.containsKey(previous.getId()) || !passed(mine.getOrDefault(previous.getId(), List.of()))) locked.put(t.getId(), previous.getTitle());
            }
            previous = t;
        }
        return locked;
    }

    /** The student's college, or null for a candidate not linked to a college. */
    private UUID collegeOf(UUID userId) {
        return students.findFirstByUserIdAndDeletedAtIsNull(userId).map(com.proveyu.college.domain.CollegeStudent::getCollegeId).orElse(null);
    }

    /** Platform tests are for everyone; a college's own tests only for its students. */
    private static boolean visibleTo(PrepTest t, UUID studentCollege) {
        return t.getCollegeId() == null || t.getCollegeId().equals(studentCollege);
    }

    /** Some submitted attempt scored strictly more than unlockPercent of its maximum (exact, no rounding). */
    private boolean passed(List<PrepAttempt> attempts) {
        return attempts.stream().anyMatch(x -> x.getSubmittedAt() != null && x.getScore() != null && x.getMaxScore() > 0
                && x.getScore().multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf((long) unlockPercent * x.getMaxScore())) > 0);
    }

    /** Best score of the submitted attempts, in percent of the attempt's maximum (floor), or null if none. */
    private static Integer bestPercent(List<PrepAttempt> attempts) {
        return attempts.stream().filter(x -> x.getSubmittedAt() != null && x.getScore() != null && x.getMaxScore() > 0)
                .map(x -> x.getScore().multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(x.getMaxScore()), 0, java.math.RoundingMode.FLOOR).intValue())
                .max(Integer::compare).orElse(null);
    }

    /** Resumes the open attempt for this test, or starts a new one. */
    @Transactional
    public AttemptView start(UUID userId, UUID testId) {
        PrepTest t = test(testId);
        if (!t.isPublished() || !visibleTo(t, collegeOf(userId))) throw notFound("Test");
        PrepAttempt open = attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .filter(a -> a.getTestId().equals(testId) && a.getSubmittedAt() == null)
                .peek(this::autoSubmitIfExpired).filter(a -> a.getSubmittedAt() == null).findFirst().orElse(null);
        if (open == null) {
            if (t.getLevelOrder() != null) {
                Map<UUID, List<PrepAttempt>> mine = attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                        .collect(Collectors.groupingBy(PrepAttempt::getTestId));
                String after = lockedTests(testRepository.findAllByPublishedTrueAndLevelOrderNotNullOrderByLevelOrderAsc(), mine).get(t.getId());
                if (after != null) {
                    throw new DomainException("This test is locked. Score more than " + unlockPercent + "% in \"" + after + "\" to unlock it.",
                            HttpStatus.FORBIDDEN, "TEST_LOCKED");
                }
            }
            Instant now = Instant.now();
            open = attemptRepository.save(PrepAttempt.builder().testId(testId).userId(userId).startedAt(now)
                    .deadlineAt(now.plus(Duration.ofMinutes(t.getDurationMinutes()))).maxScore(t.getTotalMarks()).answersJson("{}").build());
        }
        return view(t, open);
    }

    @Transactional
    public AttemptView attempt(UUID userId, UUID attemptId) {
        PrepAttempt a = own(userId, attemptId);
        autoSubmitIfExpired(a);
        if (a.getSubmittedAt() != null) throw new DomainException("This attempt is already submitted", HttpStatus.CONFLICT, "ATTEMPT_SUBMITTED");
        return view(test(a.getTestId()), a);
    }

    /** Autosave: merges the given answers into the attempt. */
    @Transactional
    public void saveAnswers(UUID userId, UUID attemptId, Map<String, Answer> answers) {
        PrepAttempt a = openAttempt(userId, attemptId);
        Map<String, Answer> merged = answers(a);
        if (answers != null) merged.putAll(answers);
        a.setAnswersJson(write(merged));
        attemptRepository.save(a);
    }

    /** "Run" button: runs the student's code against the visible sample cases only. */
    public RunResponse run(UUID userId, UUID attemptId, RunRequest r) {
        PrepAttempt a = openAttempt(userId, attemptId);
        Question q = question(test(a.getTestId()), r.questionId());
        if (!CODING.equals(q.type())) throw new DomainException("Not a coding question", HttpStatus.BAD_REQUEST, "NOT_CODING");
        return execute(q.samples() == null ? List.of() : q.samples(), r.language(), r.code(), false);
    }

    @Transactional
    public AttemptResult submit(UUID userId, UUID attemptId, Map<String, Answer> answers) {
        PrepAttempt a = own(userId, attemptId);
        if (a.getSubmittedAt() != null) return result(userId, attemptId);
        if (answers != null && Instant.now().isBefore(a.getDeadlineAt().plus(GRACE))) {
            Map<String, Answer> merged = answers(a);
            merged.putAll(answers);
            a.setAnswersJson(write(merged));
        }
        grade(a);
        return result(userId, attemptId);
    }

    @Transactional
    public AttemptResult result(UUID userId, UUID attemptId) {
        PrepAttempt a = own(userId, attemptId);
        autoSubmitIfExpired(a);
        if (a.getSubmittedAt() == null) throw new DomainException("Submit the test to see your result", HttpStatus.CONFLICT, "ATTEMPT_OPEN");
        try {
            return json.readValue(a.getResultJson(), AttemptResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable result for attempt " + attemptId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<AttemptHistory> history(UUID userId) {
        Map<UUID, String> titles = testRepository.findAll().stream().collect(Collectors.toMap(PrepTest::getId, PrepTest::getTitle));
        return attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream().filter(a -> a.getSubmittedAt() != null)
                .map(a -> new AttemptHistory(a.getId(), a.getTestId(), titles.getOrDefault(a.getTestId(), "Deleted test"), a.getStartedAt(),
                        a.getSubmittedAt(), a.getScore(), a.getMaxScore())).toList();
    }

    // ================================================================== grading

    private void autoSubmitIfExpired(PrepAttempt a) {
        if (a.getSubmittedAt() == null && Instant.now().isAfter(a.getDeadlineAt().plus(GRACE))) grade(a);
    }

    private void grade(PrepAttempt a) {
        PrepTest t = test(a.getTestId());
        TestDefinition def = definition(t);
        Map<String, Answer> ans = answers(a);
        List<QuestionResult> results = new ArrayList<>();
        Map<String, BigDecimal> secScore = new LinkedHashMap<>();
        Map<String, int[]> secStats = new LinkedHashMap<>(); // max, correct, total
        BigDecimal total = BigDecimal.ZERO;
        for (Section s : def.sections()) {
            for (Question q : s.questions()) {
                Answer given = ans.get(q.id());
                int marks = q.marksOrDefault();
                BigDecimal awarded;
                QuestionResult qr;
                if (MCQ.equals(q.type())) {
                    Integer opt = given == null ? null : given.option();
                    boolean ok = opt != null && opt.equals(q.answer());
                    awarded = ok ? BigDecimal.valueOf(marks) : BigDecimal.ZERO;
                    qr = new QuestionResult(q.id(), MCQ, s.name(), q.title(), q.text(), q.options(), opt, q.answer(), q.explanation(),
                            null, null, null, null, null, awarded, marks);
                } else {
                    String code = given == null ? null : given.code();
                    String lang = given == null ? null : given.language();
                    int passed = 0, totalTests = q.tests().size();
                    String compileError = null;
                    if (code != null && !code.isBlank() && lang != null) {
                        try {
                            RunResponse rr = execute(q.tests(), lang, code, true);
                            compileError = rr.compileError();
                            passed = (int) rr.cases().stream().filter(CaseResult::passed).count();
                        } catch (DomainException e) {
                            compileError = e.getMessage();
                        }
                    }
                    awarded = BigDecimal.valueOf(marks).multiply(BigDecimal.valueOf(passed))
                            .divide(BigDecimal.valueOf(Math.max(totalTests, 1)), 2, RoundingMode.HALF_UP);
                    qr = new QuestionResult(q.id(), CODING, s.name(), q.title(), q.text(), null, null, null, q.explanation(), lang, code,
                            passed, totalTests, compileError, awarded, marks);
                }
                results.add(qr);
                total = total.add(awarded);
                secScore.merge(s.name(), awarded, BigDecimal::add);
                int[] st = secStats.computeIfAbsent(s.name(), k -> new int[3]);
                st[0] += marks;
                st[1] += awarded.compareTo(BigDecimal.valueOf(marks)) == 0 ? 1 : 0;
                st[2] += 1;
            }
        }
        Instant now = Instant.now();
        Instant submitted = now.isAfter(a.getDeadlineAt()) ? a.getDeadlineAt() : now;
        List<SectionScore> sections = secStats.entrySet().stream()
                .map(e -> new SectionScore(e.getKey(), secScore.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        int percent = t.getTotalMarks() == 0 ? 0 : total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(t.getTotalMarks()), 0, RoundingMode.HALF_UP).intValue();
        AttemptResult result = new AttemptResult(a.getId(), t.getId(), t.getTitle(), t.getCompanyTag(), a.getStartedAt(), submitted,
                Duration.between(a.getStartedAt(), submitted).toSeconds(), total, t.getTotalMarks(), percent, sections, results);
        a.setScore(total);
        a.setSubmittedAt(submitted);
        a.setResultJson(write(result));
        attemptRepository.save(a);
    }

    /** Runs code against cases. Hidden cases never echo their input or expected output back to the student. */
    private RunResponse execute(List<IoCase> cases, String language, String code, boolean hidden) {
        DockerCodeRunner.Result r = runner.run(language, code, cases.stream().map(IoCase::input).toList());
        if (r.compileError() != null) return new RunResponse(r.compileError(), List.of());
        List<CaseResult> out = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            DockerCodeRunner.Output o = r.outputs().get(i);
            boolean passed = !o.timedOut() && o.exitCode() == 0 && DockerCodeRunner.sameOutput(cases.get(i).output(), o.stdout());
            String error = o.timedOut() ? "Time limit exceeded" : o.exitCode() != 0 ? (o.stderr().isBlank() ? "Exited with code " + o.exitCode() : o.stderr()) : null;
            out.add(hidden ? new CaseResult(i + 1, passed, null, null, null, null, o.timedOut())
                    : new CaseResult(i + 1, passed, cases.get(i).input(), cases.get(i).output(), o.stdout(), error, o.timedOut()));
        }
        return new RunResponse(null, out);
    }

    // ================================================================== helpers

    private AttemptView view(PrepTest t, PrepAttempt a) {
        List<QuestionView> qs = new ArrayList<>();
        for (Section s : definition(t).sections()) for (Question q : s.questions()) {
            qs.add(new QuestionView(q.id(), q.type(), s.name(), q.title(), q.text(), q.options(), q.marksOrDefault(),
                    CODING.equals(q.type()) ? q.starterCode() : null, CODING.equals(q.type()) ? q.samples() : null));
        }
        return new AttemptView(a.getId(), t.getId(), t.getTitle(), t.getCompanyTag(), t.getDurationMinutes(), a.getStartedAt(), a.getDeadlineAt(),
                Instant.now(), PrepModel.LANGUAGES, qs, answers(a));
    }

    private TestDefinition validate(TestDefinition d) {
        List<String> errors = new ArrayList<>();
        if (d.title() == null || d.title().isBlank()) errors.add("title is required");
        if (d.durationMinutes() == null || d.durationMinutes() < 5 || d.durationMinutes() > 300) errors.add("durationMinutes must be 5-300");
        if (d.sections() == null || d.sections().isEmpty()) errors.add("at least one section is required");
        List<Section> sections = new ArrayList<>();
        int n = 0;
        if (d.sections() != null) for (int si = 0; si < d.sections().size(); si++) {
            Section s = d.sections().get(si);
            String where = "section " + (si + 1);
            if (s.name() == null || s.name().isBlank()) errors.add(where + ": name is required");
            if (s.questions() == null || s.questions().isEmpty()) { errors.add(where + ": no questions"); continue; }
            List<Question> qs = new ArrayList<>();
            for (int qi = 0; qi < s.questions().size(); qi++) {
                Question q = s.questions().get(qi);
                String w = where + " question " + (qi + 1);
                String type = q.type() == null ? MCQ : q.type().toUpperCase(Locale.ROOT);
                if (q.text() == null || q.text().isBlank()) errors.add(w + ": text is required");
                if (MCQ.equals(type)) {
                    if (q.options() == null || q.options().size() < 2 || q.options().size() > 6) errors.add(w + ": MCQ needs 2-6 options");
                    else if (q.answer() == null || q.answer() < 0 || q.answer() >= q.options().size()) errors.add(w + ": answer must be the 0-based index of an option");
                } else if (CODING.equals(type)) {
                    if (q.tests() == null || q.tests().isEmpty()) errors.add(w + ": coding question needs at least one hidden test");
                    if (q.title() == null || q.title().isBlank()) errors.add(w + ": coding question needs a title");
                    if (q.starterCode() != null) q.starterCode().keySet().stream().filter(k -> !PrepModel.LANGUAGES.contains(k))
                            .forEach(k -> errors.add(w + ": unknown starterCode language '" + k + "' (use " + PrepModel.LANGUAGES + ")"));
                } else errors.add(w + ": type must be MCQ or CODING");
                qs.add(new Question("q" + (++n), type, q.title(), q.text(), q.options(), q.answer(), q.marks(), q.explanation(), q.starterCode(),
                        q.samples(), q.tests()));
            }
            sections.add(new Section(s.name() == null ? "" : s.name().trim(), qs));
        }
        if (!errors.isEmpty()) {
            throw new DomainException("The test file has problems: " + String.join("; ", errors.subList(0, Math.min(errors.size(), 8)))
                    + (errors.size() > 8 ? " (+" + (errors.size() - 8) + " more)" : ""), HttpStatus.BAD_REQUEST, "INVALID_TEST");
        }
        String level = d.level() == null || d.level().isBlank() ? null : d.level().trim().toUpperCase(Locale.ROOT);
        if (level != null && !List.of("EASY", "MEDIUM", "HARD").contains(level)) {
            throw new DomainException("The test file has problems: level must be EASY, MEDIUM or HARD", HttpStatus.BAD_REQUEST, "INVALID_TEST");
        }
        if (d.order() != null && (d.order() < 1 || d.order() > 1000)) {
            throw new DomainException("The test file has problems: order must be 1-1000", HttpStatus.BAD_REQUEST, "INVALID_TEST");
        }
        return new TestDefinition(d.title(), d.description(), d.companyTag(), level, d.order(), d.durationMinutes(), sections);
    }

    private TestDefinition parse(String raw) {
        try {
            return json.readValue(raw, TestDefinition.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new DomainException("The file is not valid test JSON: " + e.getOriginalMessage(), HttpStatus.BAD_REQUEST, "INVALID_TEST");
        }
    }

    private TestDefinition definition(PrepTest t) {
        try {
            return json.readValue(t.getDefinitionJson(), TestDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable definition for test " + t.getId(), e);
        }
    }

    private Question question(PrepTest t, String id) {
        return definition(t).sections().stream().flatMap(s -> s.questions().stream()).filter(q -> q.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("Question"));
    }

    private Map<String, Answer> answers(PrepAttempt a) {
        try {
            return a.getAnswersJson() == null ? new HashMap<>() : new HashMap<>(json.readValue(a.getAnswersJson(), new TypeReference<Map<String, Answer>>() {}));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private PrepAttempt own(UUID userId, UUID attemptId) {
        PrepAttempt a = attemptRepository.findById(attemptId).orElseThrow(() -> notFound("Attempt"));
        if (!a.getUserId().equals(userId)) throw notFound("Attempt");
        return a;
    }

    private PrepAttempt openAttempt(UUID userId, UUID attemptId) {
        PrepAttempt a = own(userId, attemptId);
        if (a.getSubmittedAt() != null) throw new DomainException("This attempt is already submitted", HttpStatus.CONFLICT, "ATTEMPT_SUBMITTED");
        if (Instant.now().isAfter(a.getDeadlineAt().plus(GRACE))) throw new DomainException("Time is up for this attempt", HttpStatus.CONFLICT, "ATTEMPT_EXPIRED");
        return a;
    }

    private PrepTest test(UUID id) {
        return testRepository.findById(id).orElseThrow(() -> notFound("Test"));
    }

    private AdminTestSummary summary(PrepTest t, long attempts) {
        return summary(t, attempts, 0, null);
    }

    private AdminTestSummary summary(PrepTest t, long attempts, long students, Integer averagePercent) {
        return new AdminTestSummary(t.getId(), t.getTitle(), t.getDescription(), t.getCompanyTag(), t.getDurationMinutes(), t.getQuestionCount(),
                t.getCodingCount(), t.getTotalMarks(), t.isPublished(), attempts, t.getCreatedAt(), t.getLevel(), t.getLevelOrder(), students,
                averagePercent);
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DomainException notFound(String what) {
        return new DomainException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
