package com.proveyu.student.codeground;

import com.proveyu.shared.error.DomainException;
import com.proveyu.student.codeground.CodeGroundContent.Located;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * The problem of each day. An admin may choose it ahead; a day without a choice gets one automatically the first time anyone asks,
 * and keeps it, so adding problems never changes today's problem under the students' feet.
 */
@Service
@RequiredArgsConstructor
public class CodeGroundSchedule {

    private static final int MAX_DAYS = 90;

    /** problemKey null: nothing chosen yet, it will be picked automatically on the day. */
    public record Day(String date, String problemKey, String title, String difficulty, boolean auto, boolean unavailable, int bonusesGiven) {}

    private final CodeGroundContent content;
    private final CodeGroundAuthored store;

    public Located problemOf(LocalDate day) {
        CodeGroundAuthored.Scheduled chosen = store.schedule(day, day).get(day);
        if (chosen != null) {
            Optional<Located> found = content.problem(chosen.problemKey());
            if (found.isPresent()) return found.get();
        }
        List<Located> all = content.topics().stream().flatMap(t -> t.problems().stream().map(p -> new Located(t, p))).toList();
        if (all.isEmpty()) throw new DomainException("No problems are available", HttpStatus.NOT_FOUND, "NOT_FOUND");
        Located pick = all.get(CodeGroundDays.problemOfDay(day, all.size()));
        store.scheduleAuto(day, pick.problem().key(), chosen != null); // replace a choice that is no longer offered
        // another request may have recorded first: everyone uses what is stored
        return content.problem(store.schedule(day, day).get(day).problemKey()).orElse(pick);
    }

    /** Today and the following days. */
    public List<Day> upcoming(int days) {
        LocalDate today = CodeGroundDays.today();
        int n = Math.max(1, Math.min(MAX_DAYS, days));
        problemOf(today);
        Map<LocalDate, CodeGroundAuthored.Scheduled> chosen = store.schedule(today, today.plusDays(n - 1));
        List<Day> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = today.plusDays(i);
            CodeGroundAuthored.Scheduled c = chosen.get(d);
            int given = i == 0 ? store.bonusesGiven(d) : 0;
            if (c == null) {
                out.add(new Day(d.toString(), null, null, null, true, false, given));
                continue;
            }
            Optional<Located> p = content.problem(c.problemKey());
            out.add(new Day(d.toString(), c.problemKey(), p.map(l -> l.problem().title()).orElse(c.problemKey()),
                    p.map(l -> l.problem().difficulty()).orElse(null), c.auto(), p.isEmpty(), given));
        }
        return out;
    }

    public void set(LocalDate day, String problemKey) {
        checkOpen(day);
        if (content.problem(problemKey).isEmpty()) {
            throw new DomainException("Choose a published problem", HttpStatus.BAD_REQUEST, "UNKNOWN_PROBLEM");
        }
        store.schedule(day, problemKey);
    }

    /** Back to the automatic choice. */
    public void clear(LocalDate day) {
        checkOpen(day);
        store.unschedule(day);
    }

    private static void checkOpen(LocalDate day) {
        LocalDate today = CodeGroundDays.today();
        if (day.isBefore(today) || day.isAfter(today.plusDays(365))) {
            throw new DomainException("Only today and the coming year can be changed", HttpStatus.BAD_REQUEST, "BAD_DATE");
        }
    }
}
