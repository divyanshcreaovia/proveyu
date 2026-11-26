package com.proveyu.student.codeground;

import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import com.proveyu.student.application.StudentPortalService;
import com.proveyu.student.codeground.CodeGroundProgress.Finished;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Code ground ranking inside one college. Points are worked out from what is finished (nothing extra is stored): Easy 10, Medium 20,
 * Hard 40, a learning guide 5, each counted once, plus the problem-of-the-day bonuses. "week" counts what was finished since Monday
 * 00:00 India time. Equal points: the student who got there first is ahead. Streak: see {@link CodeGroundDays}.
 */
@Service
public class CodeGroundLeaderboard {

    private static final int GUIDE_POINTS = 5;
    private static final int TOP = 10;
    private static final int STAFF_ROWS = 500;

    public enum Period { week, all }

    public enum Scope { college, branch, batch }

    /** One ranked student as other students see them: name and branch only. */
    public record Row(int rank, String name, String branch, int batchYear, int points, int solved, int streak, boolean you) {}

    /** rank is null until the student has points in this view. pointsToNext: how many more to pass the student just above. */
    public record Standing(Integer rank, int points, int solved, int streak, Integer pointsToNext) {}

    public record Board(Period period, Scope scope, LocalDate weekStart, int participants, List<Row> top, Standing me, String myBranch,
                        int myBatchYear) {}

    public record StaffRow(int rank, String name, String rollNumber, String branch, int batchYear, int points, int solved, int streak,
                           LocalDate lastActive) {}

    public record StaffBoard(Period period, LocalDate weekStart, int participants, List<StaffRow> rows) {}

    private record Score(Finished student, int points, int solved, int streak, LocalDateTime reachedAt, LocalDate lastActive) {}

    private final CodeGroundContent content;
    private final CodeGroundProgress progress;
    private final StudentPortalService students;

    /** A college's finished items, kept for a moment: a whole class opening the board reads the database once, not once each. */
    private record Cached(List<Finished> items, long at) {}

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final long cacheNanos;

    public CodeGroundLeaderboard(CodeGroundContent content, CodeGroundProgress progress, StudentPortalService students,
                                 @Value("${code.ground.leaderboard-cache-seconds:20}") int cacheSeconds) {
        this.cacheNanos = Math.max(0, cacheSeconds) * 1_000_000_000L;
        this.content = content;
        this.progress = progress;
        this.students = students;
    }

    /** A student's points changed: their college's board is rebuilt on the next request instead of after the cache time. */
    public void changed(UUID userId) {
        try {
            cache.remove(students.me(userId).getCollegeId());
        } catch (DomainException notInACollege) {
            // a user without a college has no board
        }
    }

    public Board forStudent(UUID userId, String period, String scope) {
        Period p = parse(Period.class, period, Period.week);
        Scope s = parse(Scope.class, scope, Scope.college);
        CollegeStudent me = students.me(userId);
        Predicate<Finished> filter = switch (s) {
            case college -> f -> true;
            case branch -> f -> Objects.equals(f.branchId(), me.getBranchId());
            case batch -> f -> f.batchYear() == me.getBatchYear();
        };
        List<Finished> mine = finished(me.getCollegeId());
        List<Score> ranked = rank(mine.stream().filter(filter).toList(), p);

        List<Row> top = new ArrayList<>();
        Standing standing = null;
        for (int i = 0; i < ranked.size(); i++) {
            Score x = ranked.get(i);
            boolean you = x.student().userId().equals(userId);
            if (i < TOP) top.add(new Row(i + 1, x.student().name(), x.student().branch(), x.student().batchYear(), x.points(), x.solved(), x.streak(), you));
            if (you) standing = new Standing(i + 1, x.points(), x.solved(), x.streak(), i == 0 ? null : ranked.get(i - 1).points() - x.points() + 1);
        }
        if (standing == null) { // no points in this view yet; a streak from earlier days still shows
            Set<LocalDate> days = days(mine.stream().filter(f -> f.userId().equals(userId)).toList());
            standing = new Standing(null, 0, 0, CodeGroundDays.currentStreak(days), null);
        }
        String branch = mine.stream().filter(f -> f.userId().equals(userId)).map(Finished::branch).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> students.profile(userId).branch());
        return new Board(p, s, weekStart(), ranked.size(), top, standing, branch, me.getBatchYear());
    }

    /** For college staff; a coordinator limited to some branches sees only those. */
    public StaffBoard forStaff(CollegeContext ctx, String period, UUID branchId, Integer batchYear) {
        Period p = parse(Period.class, period, Period.week);
        List<Finished> items = finished(ctx.collegeId()).stream()
                .filter(f -> ctx.canAccessBranch(f.branchId()))
                .filter(f -> branchId == null || branchId.equals(f.branchId()))
                .filter(f -> batchYear == null || f.batchYear() == batchYear)
                .toList();
        List<Score> ranked = rank(items, p);
        List<StaffRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(STAFF_ROWS, ranked.size()); i++) {
            Score x = ranked.get(i);
            Finished st = x.student();
            rows.add(new StaffRow(i + 1, st.name(), st.rollNumber(), st.branch(), st.batchYear(), x.points(), x.solved(), x.streak(), x.lastActive()));
        }
        return new StaffBoard(p, weekStart(), ranked.size(), rows);
    }

    private List<Finished> finished(UUID collegeId) {
        Cached c = cache.get(collegeId);
        if (c == null || System.nanoTime() - c.at() >= cacheNanos) {
            c = new Cached(progress.ofCollege(collegeId), System.nanoTime());
            if (cacheNanos > 0) cache.put(collegeId, c);
        }
        return c.items();
    }

    private List<Score> rank(List<Finished> items, Period period) {
        LocalDateTime since = period == Period.week
                ? weekStart().atStartOfDay(CodeGroundDays.ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
        List<Score> scores = new ArrayList<>();
        for (List<Finished> own : items.stream().collect(Collectors.groupingBy(Finished::userId)).values()) {
            int points = 0, solved = 0;
            LocalDateTime reachedAt = null;
            for (Finished f : own) {
                if (since != null && f.doneAt().isBefore(since)) continue;
                int worth = worth(f);
                if (worth == 0) continue;
                points += worth;
                if (f.type() == CodeGroundProgress.Type.PROBLEM) solved++;
                if (reachedAt == null || f.doneAt().isAfter(reachedAt)) reachedAt = f.doneAt();
            }
            if (points == 0) continue;
            LocalDate last = own.stream().map(f -> CodeGroundDays.of(f.doneAt())).max(Comparator.naturalOrder()).orElse(null);
            scores.add(new Score(own.get(0), points, solved, CodeGroundDays.currentStreak(days(own)), reachedAt, last));
        }
        scores.sort(Comparator.comparingInt(Score::points).reversed()
                .thenComparing(Score::reachedAt)
                .thenComparing(s -> s.student().name())
                .thenComparing(s -> s.student().userId()));
        return scores;
    }

    /** 0 for a problem or topic that is no longer part of Code ground; a daily bonus keeps what was given. */
    private int worth(Finished f) {
        if (f.type() == CodeGroundProgress.Type.DAILY) return f.points();
        if (f.type() == CodeGroundProgress.Type.GUIDE) return content.topic(f.key()).isPresent() ? GUIDE_POINTS : 0;
        // also counts a problem that has since been taken out of use
        return content.difficultyOf(f.key()).map(d -> CodeGroundDays.PROBLEM_POINTS.getOrDefault(d, 0)).orElse(0);
    }

    private static Set<LocalDate> days(List<Finished> own) {
        return own.stream().filter(f -> f.type() != CodeGroundProgress.Type.GUIDE).map(f -> CodeGroundDays.of(f.doneAt()))
                .collect(Collectors.toSet());
    }

    private static LocalDate weekStart() {
        return CodeGroundDays.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Unknown value: " + value, HttpStatus.BAD_REQUEST, "BAD_FILTER");
        }
    }
}
