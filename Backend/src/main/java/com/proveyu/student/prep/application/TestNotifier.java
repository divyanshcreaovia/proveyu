package com.proveyu.student.prep.application;

import com.proveyu.student.prep.domain.PrepTest;
import com.proveyu.student.prep.infrastructure.PrepTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bell notifications to students about their college's tests: published, rescheduled, live now (sent by a scheduler at the start
 * time), withdrawn, results released, extra time, written answers marked. One batch insert per message, so a whole batch of
 * students is notified in one go.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestNotifier {

    static final String TYPE = "COLLEGE_TEST";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcTemplate jdbc;
    private final PrepTestRepository tests;

    /** Every published exam whose window has started gets one "live now" notice to the eligible students who have not taken it. */
    @Scheduled(fixedDelayString = "${prep.exam-live-check-ms:60000}", initialDelay = 30000)
    @Transactional
    public void announceLiveExams() {
        Instant now = Instant.now();
        for (PrepTest t : tests.findAllByModeAndPublishedTrueAndLiveNotifiedAtIsNullAndStartsAtLessThanEqualAndEndsAtGreaterThan("EXAM", now, now)) {
            Set<String> users = new LinkedHashSet<>(eligibleUsers(t));
            users.removeAll(jdbc.queryForList("select user_id from prep_attempts where test_id = ?", String.class, t.getId().toString()));
            send(users, "Exam \"" + t.getTitle() + "\" is live now. Start it in College tests before " + WHEN.format(t.getEndsAt()) + " (one attempt).");
            t.setLiveNotifiedAt(now);
            tests.save(t);
            log.info("[EXAM] \"{}\" is live: {} students notified", t.getTitle(), users.size());
        }
    }

    public void published(PrepTest t) {
        boolean liveNow = t.isExam() && t.getStartsAt() != null && !Instant.now().isBefore(t.getStartsAt());
        send(eligibleUsers(t), liveNow
                ? "New exam from your college: \"" + t.getTitle() + "\" is live now. Start it in College tests before " + WHEN.format(t.getEndsAt())
                        + " (" + t.getDurationMinutes() + " min, one attempt)."
                : t.isExam()
                ? "New exam from your college: \"" + t.getTitle() + "\", " + WHEN.format(t.getStartsAt()) + " – " + WHEN.format(t.getEndsAt())
                        + " (" + t.getDurationMinutes() + " min, one attempt). It opens by itself at the start time in College tests."
                : "New practice test from your college: \"" + t.getTitle() + "\" (" + t.getDurationMinutes() + " min). Open College tests to take it.");
    }

    public void withdrawn(PrepTest t) {
        send(eligibleUsers(t), (t.isExam() ? "Exam" : "Test") + " \"" + t.getTitle() + "\" was withdrawn by your college.");
    }

    public void rescheduled(PrepTest t) {
        send(eligibleUsers(t), "Exam \"" + t.getTitle() + "\" was rescheduled: now " + WHEN.format(t.getStartsAt()) + " – " + WHEN.format(t.getEndsAt()) + ".");
    }

    public void resultsReleased(PrepTest t) {
        send(attemptUsers(t), "Results of \"" + t.getTitle() + "\" are out. Open College tests to see your score.");
    }

    public void extraTime(PrepTest t, UUID userId, int minutes) {
        send(List.of(userId.toString()), "Your placement cell gave you " + minutes + " extra minutes on \"" + t.getTitle() + "\".");
    }

    public void marked(PrepTest t, UUID userId) {
        if (t.isExam() && !t.isResultsReleased()) return; // they hear it with the results
        send(List.of(userId.toString()), "Your written answers in \"" + t.getTitle() + "\" were marked. Open the result to see your score.");
    }

    /** Students of the test's college who can see it: active students with an account, and for an exam its branches / batch years. */
    List<String> eligibleUsers(PrepTest t) {
        if (t.getCollegeId() == null) return List.of();
        List<String> branches = PrepService.csv(t.getBranchIds()), years = PrepService.csv(t.getBatchYears());
        StringBuilder sql = new StringBuilder("select user_id from college_students where college_id = ? and deleted_at is null and user_id is not null"
                + " and status in ('ACTIVE', 'PLACED')");
        List<Object> args = new ArrayList<>(List.of(t.getCollegeId().toString()));
        if (t.isExam() && !branches.isEmpty()) {
            sql.append(" and branch_id in (").append(String.join(",", Collections.nCopies(branches.size(), "?"))).append(")");
            args.addAll(branches);
        }
        if (t.isExam() && !years.isEmpty()) {
            sql.append(" and batch_year in (").append(String.join(",", Collections.nCopies(years.size(), "?"))).append(")");
            years.forEach(y -> args.add(Integer.valueOf(y)));
        }
        return jdbc.queryForList(sql.toString(), String.class, args.toArray());
    }

    private List<String> attemptUsers(PrepTest t) {
        return jdbc.queryForList("select distinct user_id from prep_attempts where test_id = ? and submitted_at is not null", String.class, t.getId().toString());
    }

    private void send(Collection<String> userIds, String message) {
        if (userIds.isEmpty()) return;
        jdbc.batchUpdate("insert into notifications (id, user_id, type, message, is_read) values (?, ?, '" + TYPE + "', ?, 0)",
                userIds.stream().distinct().map(u -> new Object[]{UUID.randomUUID().toString(), u, message}).toList());
    }
}
