package com.proveyu.student.codeground;

import com.proveyu.shared.error.DomainException;
import com.proveyu.student.codeground.CodeGroundContent.Located;
import com.proveyu.student.codeground.CodeGroundContent.Problem;
import com.proveyu.student.codeground.CodeGroundContent.Slide;
import com.proveyu.student.codeground.CodeGroundContent.Topic;
import com.proveyu.student.prep.application.CodeExecutionService;
import com.proveyu.student.prep.application.CodeRunRateLimiter;
import com.proveyu.student.prep.domain.PrepModel.IoCase;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Practice problems by topic. A problem is solved only when the server has run the student's program on every test and all
 * passed, so the browser cannot mark anything solved. Code runs on the same judge as the mock tests.
 */
@Service
public class CodeGroundService {

    /** The languages a problem can be solved in, in the order shown. Every problem has a starter for each. */
    public record Language(String key, String label) {}

    static final List<Language> LANGUAGES = List.of(
            new Language("javascript", "JavaScript (Node.js 12)"),
            new Language("python", "Python 3.8"),
            new Language("java", "Java 13"),
            new Language("cpp", "C++ (GCC 9)"));
    private static final int MAX_CODE_BYTES = 65_536;
    private static final int MAX_ERROR_CHARS = 1_500;

    /** attempted = run at least once without passing every test (and not solved since). */
    public record ProblemCard(String key, String title, String difficulty, boolean solved, boolean attempted) {}

    /** done / total count the problems and the learning guide together (the progress ring). */
    public record TopicCard(String key, String title, String summary, int guideSlides, boolean guideRead, int solved, int done, int total,
                            List<ProblemCard> problems) {}

    public record Overview(List<Language> languages, List<TopicCard> topics, DailyProblem daily) {}

    /** The problem of the day. solvedToday: today's bonus has been earned. */
    public record DailyProblem(String date, String key, String topicKey, String title, String difficulty, int bonus, boolean solvedToday) {}

    public record ActivityDay(String date, int count) {}

    public record SubmissionFilter(Integer days, LocalDate from, LocalDate to, String verdict, String language, String difficulty, String q,
                                   boolean stuck, int page) {}

    /** available: the problem can still be opened. */
    public record MySubmission(long id, String problemKey, String title, String difficulty, String language, String verdict, int passed,
                               int total, String createdAt, boolean available) {}

    public record SubmissionSummary(int total, int accepted, int solvedProblems, int problems, String topLanguage) {}

    public record MySubmissions(SubmissionSummary summary, List<MySubmission> items, boolean hasMore) {}

    public record SubmissionCode(String code) {}

    /** The last year, India days. count: problems solved that day (newly, or the problem of the day). */
    public record Activity(String from, String to, List<ActivityDay> days, int currentStreak, int longestStreak, int activeDays, int solved) {}

    public record Guide(String topicKey, String title, List<Slide> slides, boolean read) {}

    public record ProblemView(String key, String topicKey, String title, String difficulty, String statement, List<String> inputFormat,
                              IoCase example, String hint, String note, Map<String, String> starters, int testCount, int hiddenCount, boolean solved,
                              Map<String, String> drafts) {}

    /** One visible test. actual is what the program answered; printed is what the student printed while debugging. */
    public record Case(int index, boolean passed, String input, String expected, String actual, String printed, String error, boolean timedOut) {}

    /** A hidden test says only whether it passed: never the input, the answer, the output or an error message. */
    public record HiddenCase(int index, boolean passed, boolean timedOut) {}

    /** Submit: hidden is empty until every visible test passes. */
    /** bonus: problem-of-the-day points earned by this submission (0 if none). */
    public record RunResult(String compileError, List<Case> cases, List<HiddenCase> hidden, boolean allPassed, boolean solved, int bonus) {}

    /** Run: one input, the example or the student's own. expected and passed are null when the answer is not known (own input). */
    public record Sample(String compileError, String input, String expected, String actual, String printed, String error, boolean timedOut,
                         Boolean passed) {}

    private static final int MAX_INPUT_BYTES = 65_536;

    private final CodeGroundContent content;
    private final CodeGroundProgress progress;
    private final CodeExecutionService runner;
    private final CodeRunRateLimiter rateLimiter;
    private final CodeGroundDrafts drafts;
    private final CodeGroundHistory history;
    private final CodeGroundSchedule schedule;
    private final CodeGroundLeaderboard leaderboard;

    public CodeGroundService(CodeGroundContent content, CodeGroundProgress progress, CodeExecutionService runner, CodeRunRateLimiter rateLimiter,
                             CodeGroundDrafts drafts, CodeGroundHistory history, CodeGroundSchedule schedule, CodeGroundLeaderboard leaderboard) {
        this.leaderboard = leaderboard;
        this.schedule = schedule;
        this.history = history;
        this.drafts = drafts;
        this.content = content;
        this.progress = progress;
        this.runner = runner;
        this.rateLimiter = rateLimiter;
    }

    public Overview overview(UUID userId) {
        CodeGroundProgress.Done done = progress.done(userId);
        List<TopicCard> cards = content.topics().stream().map(t -> {
            List<ProblemCard> problems = t.problems().stream()
                    .map(p -> new ProblemCard(p.key(), p.title(), p.difficulty(), done.problems().contains(p.key()), done.attempted().contains(p.key()))).toList();
            int solved = (int) problems.stream().filter(ProblemCard::solved).count();
            boolean hasGuide = !t.guide().isEmpty();
            boolean read = hasGuide && done.guides().contains(t.key());
            return new TopicCard(t.key(), t.title(), t.summary(), t.guide().size(), read, solved, solved + (read ? 1 : 0),
                    problems.size() + (hasGuide ? 1 : 0), problems);
        }).toList();
        return new Overview(LANGUAGES, cards, daily(userId));
    }

    public Guide guide(UUID userId, String topicKey) {
        Topic t = topicOf(topicKey);
        return new Guide(t.key(), t.title(), t.guide(), progress.done(userId).guides().contains(t.key()));
    }

    public void completeGuide(UUID userId, String topicKey) {
        progress.guideRead(userId, topicOf(topicKey).key());
        leaderboard.changed(userId);
    }

    public ProblemView problem(UUID userId, String key) {
        Located l = located(key);
        Problem p = l.problem();
        return new ProblemView(p.key(), l.topic().key(), p.title(), p.difficulty(), p.statement(), p.inputFormat(), p.tests().get(0), p.hint(),
                p.note(), p.starters(), p.tests().size(), p.hiddenTests().size(), progress.done(userId).problems().contains(p.key()), drafts.of(userId, p.key()));
    }

    /**
     * Run: the program on one input, quickly. Nothing is marked solved here. input null = the example; the student's own input has no
     * expected answer unless it is one of the visible tests.
     */
    public Sample run(UUID userId, String key, String language, String code, String input) {
        Problem p = located(key).problem();
        String lang = languageOf(language);
        checkCode(code);
        String in = input == null ? p.tests().get(0).input() : input.replace("\r\n", "\n");
        if (in.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new DomainException("The input is too long (max 64 KB)", HttpStatus.BAD_REQUEST, "INPUT_TOO_LARGE");
        }
        rateLimiter.check(userId, CodeRunRateLimiter.Action.CODE_GROUND);
        String expected = p.tests().stream().filter(t -> t.input().stripTrailing().equals(in.stripTrailing())).map(IoCase::output)
                .findFirst().orElse(null);
        CodeExecutionService.Result result = runner.run(lang, code, List.of(in));
        progress.attempt(userId, p.key(), false);
        if (result.compileError() != null) return new Sample(result.compileError(), in, expected, "", "", null, false, null);
        CodeExecutionService.Output o = result.outputs().get(0);
        CodeGroundOutput.Parts out = CodeGroundOutput.split(o);
        Boolean passed = expected == null ? null : CodeGroundOutput.passed(o, expected);
        return new Sample(null, in, expected, cut(out.actual()), cut(out.printed()), errorOf(o), o.timedOut(), passed);
    }

    /**
     * Submit: the program on every visible test and, only if all of those pass, on the hidden ones (a second batch, so a wrong answer
     * comes back quickly). Only this can mark a problem solved. A judge that is busy or down records nothing.
     */
    public RunResult submit(UUID userId, String key, String language, String code) {
        Problem p = located(key).problem();
        String lang = languageOf(language);
        checkCode(code);
        rateLimiter.check(userId, CodeRunRateLimiter.Action.CODE_GROUND_SUBMIT);
        CodeExecutionService.Result result = runner.run(lang, code, p.tests().stream().map(IoCase::input).toList());
        List<Case> cases = new ArrayList<>();
        if (result.compileError() == null) {
            for (int i = 0; i < p.tests().size(); i++) {
                IoCase test = p.tests().get(i);
                CodeExecutionService.Output o = result.outputs().get(i);
                CodeGroundOutput.Parts out = CodeGroundOutput.split(o);
                cases.add(new Case(i + 1, CodeGroundOutput.passed(o, test.output()), test.input(), test.output(), cut(out.actual()), cut(out.printed()),
                        errorOf(o), o.timedOut()));
            }
        }
        boolean visible = !cases.isEmpty() && cases.stream().allMatch(Case::passed);
        List<HiddenCase> hidden = visible ? runHidden(lang, code, p.hiddenTests()) : List.of();
        boolean all = visible && hidden.stream().allMatch(HiddenCase::passed);
        progress.attempt(userId, p.key(), all);
        boolean solved = all || progress.done(userId).problems().contains(p.key());
        int passedCount = (int) (cases.stream().filter(Case::passed).count() + hidden.stream().filter(HiddenCase::passed).count());
        history.record(userId, p.key(), lang, code, verdict(result.compileError(), cases, hidden, all), passedCount,
                p.tests().size() + p.hiddenTests().size());
        int bonus = all ? dailyBonus(userId, p) : 0;
        if (all) leaderboard.changed(userId); // the points moved: the board must show them now
        return new RunResult(result.compileError(), cases, hidden, all, solved, bonus);
    }

    public List<CodeGroundHistory.Submission> submissions(UUID userId, String key) {
        return history.of(userId, located(key).problem().key());
    }

    private static final int PAGE = 50;

    /**
     * All of the student's kept submissions (the last 20 of each problem), filtered. days: only the last n India days (1 = today);
     * otherwise from / to. stuck: only problems not solved yet. The summary counts everything that matches, not just this page.
     */
    public MySubmissions mySubmissions(UUID userId, SubmissionFilter f) {
        LocalDate to = f.days() != null ? null : f.to();
        LocalDate from = f.days() != null ? CodeGroundDays.today().minusDays(Math.max(1, Math.min(366, f.days())) - 1L) : f.from();
        String search = f.q() == null ? "" : f.q().strip().toLowerCase(Locale.ROOT);
        Set<String> solved = progress.done(userId).problems();
        List<MySubmission> found = new ArrayList<>();
        for (CodeGroundHistory.Entry e : history.all(userId)) {
            LocalDate day = CodeGroundDays.of(e.createdAt());
            if ((from != null && day.isBefore(from)) || (to != null && day.isAfter(to))) continue;
            if (given(f.verdict()) && !e.verdict().equalsIgnoreCase(f.verdict())) continue;
            if (given(f.language()) && !e.language().equalsIgnoreCase(f.language())) continue;
            if (f.stuck() && solved.contains(e.problemKey())) continue;
            Located l = content.problem(e.problemKey()).orElse(null);
            String title = l == null ? e.problemKey() : l.problem().title();
            String difficulty = content.difficultyOf(e.problemKey()).orElse(null);
            if (given(f.difficulty()) && !f.difficulty().equalsIgnoreCase(difficulty)) continue;
            if (!search.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(search)) continue;
            found.add(new MySubmission(e.id(), e.problemKey(), title, difficulty, e.language(), e.verdict(), e.passed(), e.total(),
                    e.createdAt().toInstant(ZoneOffset.UTC).toString(), l != null));
        }
        Map<String, Long> languages = found.stream().collect(Collectors.groupingBy(MySubmission::language, Collectors.counting()));
        SubmissionSummary summary = new SubmissionSummary(found.size(),
                (int) found.stream().filter(s -> s.verdict().equals("ACCEPTED")).count(),
                (int) found.stream().filter(s -> s.verdict().equals("ACCEPTED")).map(MySubmission::problemKey).distinct().count(),
                (int) found.stream().map(MySubmission::problemKey).distinct().count(),
                languages.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null));
        int start = Math.min(found.size(), Math.max(0, f.page()) * PAGE);
        int end = Math.min(found.size(), start + PAGE);
        return new MySubmissions(summary, found.subList(start, end), end < found.size());
    }

    public SubmissionCode submissionCode(UUID userId, long id) {
        return new SubmissionCode(history.code(userId, id)
                .orElseThrow(() -> new DomainException("Submission not found", HttpStatus.NOT_FOUND, "NOT_FOUND")));
    }

    private static boolean given(String s) {
        return s != null && !s.isBlank();
    }

    public Activity activity(UUID userId) {
        Map<LocalDate, Set<String>> byDay = new TreeMap<>();
        progress.solvedAt(userId).forEach((problem, at) -> byDay.computeIfAbsent(CodeGroundDays.of(at), d -> new HashSet<>()).add(problem));
        history.dailyOf(userId).forEach(d -> byDay.computeIfAbsent(d.day(), x -> new HashSet<>()).add(d.problemKey()));
        LocalDate to = CodeGroundDays.today();
        LocalDate from = to.minusWeeks(52).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        List<ActivityDay> days = byDay.entrySet().stream().filter(e -> !e.getKey().isBefore(from))
                .map(e -> new ActivityDay(e.getKey().toString(), e.getValue().size())).toList();
        return new Activity(from.toString(), to.toString(), days, CodeGroundDays.currentStreak(byDay.keySet()),
                CodeGroundDays.longestStreak(byDay.keySet()), days.size(), progress.done(userId).problems().size());
    }

    private DailyProblem daily(UUID userId) {
        LocalDate today = CodeGroundDays.today();
        Located l = schedule.problemOf(today);
        Problem p = l.problem();
        boolean earned = history.dailyOf(userId).stream().anyMatch(d -> d.day().equals(today));
        return new DailyProblem(today.toString(), p.key(), l.topic().key(), p.title(), p.difficulty(),
                CodeGroundDays.PROBLEM_POINTS.getOrDefault(p.difficulty(), 0), earned);
    }

    /** Passing today's problem today gives its points again, once a day, also to someone who had solved it before. */
    private int dailyBonus(UUID userId, Problem p) {
        LocalDate today = CodeGroundDays.today();
        if (!schedule.problemOf(today).problem().key().equals(p.key())) return 0;
        int bonus = CodeGroundDays.PROBLEM_POINTS.getOrDefault(p.difficulty(), 0);
        return history.giveDailyBonus(userId, today, p.key(), bonus) ? bonus : 0;
    }

    private static String verdict(String compileError, List<Case> cases, List<HiddenCase> hidden, boolean all) {
        if (compileError != null) return "COMPILE_ERROR";
        if (all) return "ACCEPTED";
        if (cases.stream().anyMatch(Case::timedOut) || hidden.stream().anyMatch(HiddenCase::timedOut)) return "TIME_LIMIT";
        if (cases.stream().anyMatch(c -> c.error() != null)) return "RUNTIME_ERROR";
        return "WRONG_ANSWER";
    }

    /** Keeps the code the student is writing; empty removes it (Reset code). */
    public void saveDraft(UUID userId, String key, String language, String code) {
        Problem p = located(key).problem();
        String lang = languageOf(language);
        if (code == null || code.isBlank()) {
            drafts.delete(userId, p.key(), lang);
            return;
        }
        checkCode(code);
        drafts.save(userId, p.key(), lang, code);
    }

    private static String languageOf(String language) {
        String lang = language == null || language.isBlank() ? LANGUAGES.get(0).key() : language.toLowerCase(Locale.ROOT);
        if (LANGUAGES.stream().noneMatch(l -> l.key().equals(lang))) {
            throw new DomainException("Unsupported language: " + language, HttpStatus.BAD_REQUEST, "LANGUAGE_UNSUPPORTED");
        }
        return lang;
    }

    private static void checkCode(String code) {
        if (code == null || code.isBlank()) throw new DomainException("Write some code first", HttpStatus.BAD_REQUEST, "EMPTY_CODE");
        if (code.getBytes(StandardCharsets.UTF_8).length > MAX_CODE_BYTES) {
            throw new DomainException("Code is too long (max 64 KB)", HttpStatus.BAD_REQUEST, "CODE_TOO_LONG");
        }
    }

    private static String errorOf(CodeExecutionService.Output o) {
        if (o.timedOut()) return "Time limit exceeded";
        if (o.exitCode() == 0) return null;
        return o.stderr().isBlank() ? "Exited with code " + o.exitCode() : cut(o.stderr());
    }

    private List<HiddenCase> runHidden(String language, String code, List<IoCase> tests) {
        if (tests.isEmpty()) return List.of();
        CodeExecutionService.Result result = runner.run(language, code, tests.stream().map(IoCase::input).toList());
        List<HiddenCase> cases = new ArrayList<>();
        for (int i = 0; i < tests.size(); i++) {
            if (result.compileError() != null) {
                cases.add(new HiddenCase(i + 1, false, false));
                continue;
            }
            CodeExecutionService.Output o = result.outputs().get(i);
            boolean passed = CodeGroundOutput.passed(o, tests.get(i).output());
            cases.add(new HiddenCase(i + 1, passed, o.timedOut()));
        }
        return cases;
    }

    private static String cut(String s) {
        return s == null || s.length() <= MAX_ERROR_CHARS ? s : s.substring(0, MAX_ERROR_CHARS) + " …";
    }

    private Topic topicOf(String key) {
        return content.topic(key).orElseThrow(() -> new DomainException("Topic not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private Located located(String key) {
        return content.problem(key).orElseThrow(() -> new DomainException("Problem not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }
}
