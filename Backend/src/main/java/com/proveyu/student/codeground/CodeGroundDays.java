package com.proveyu.student.codeground;

import java.time.*;
import java.util.*;

/** Days in Code ground are India days. A streak is days in a row with a newly solved problem or the problem of the day solved. */
final class CodeGroundDays {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** Points of a problem by difficulty; the problem of the day gives the same again as a bonus. */
    static final Map<String, Integer> PROBLEM_POINTS = Map.of("EASY", 10, "MEDIUM", 20, "HARD", 40);

    private CodeGroundDays() {}

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    static LocalDate of(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDate();
    }

    /** Days in a row ending today, or yesterday (today is not over yet). */
    static int currentStreak(Set<LocalDate> days) {
        LocalDate d = today();
        if (!days.contains(d)) d = d.minusDays(1);
        int n = 0;
        while (days.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    static int longestStreak(Set<LocalDate> days) {
        int best = 0, run = 0;
        LocalDate previous = null;
        for (LocalDate d : new TreeSet<>(days)) {
            run = previous != null && previous.plusDays(1).equals(d) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = d;
        }
        return best;
    }

    /** Which of n problems is the problem of this day: every student gets the same one, and all n come up before any repeats. */
    static int problemOfDay(LocalDate day, int n) {
        int stride = Math.max(1, n * 2 / 5);
        while (gcd(stride, n) != 1) stride++;
        return (int) Math.floorMod(day.toEpochDay() * stride, (long) n);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
