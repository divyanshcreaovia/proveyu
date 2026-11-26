package com.proveyu.student.codeground;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Submissions (table code_ground_submissions) and problem-of-the-day bonuses (code_ground_daily). Every query is for one student. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CodeGroundHistory {

    /** How many submissions of one problem are kept. */
    static final int KEPT = 20;

    /** createdAt is a UTC instant, e.g. 2026-11-26T09:12:00Z. */
    public record Submission(long id, String language, String code, String verdict, int passed, int total, String createdAt) {}

    public record Daily(LocalDate day, String problemKey) {}

    /** A submission without its code. createdAt is UTC. */
    public record Entry(long id, String problemKey, String language, String verdict, int passed, int total, LocalDateTime createdAt) {}

    private final JdbcTemplate jdbc;

    public void record(UUID userId, String problemKey, String language, String code, String verdict, int passed, int total) {
        jdbc.update("""
                insert into code_ground_submissions (user_id, problem_key, language, code, verdict, passed_count, total_count, created_at)
                values (?, ?, ?, ?, ?, ?, ?, utc_timestamp())""", userId.toString(), problemKey, language, code, verdict, passed, total);
        trim(userId, problemKey);
    }

    /**
     * Drops what is older than the last {@link #KEPT}. The old ids are read first and then deleted by primary key: one statement doing
     * both locks index ranges and deadlocks when many submissions arrive together. Tidying up never fails a submission.
     */
    private void trim(UUID userId, String problemKey) {
        try {
            List<Long> old = jdbc.queryForList(
                    "select id from code_ground_submissions where user_id = ? and problem_key = ? order by id desc limit 100 offset ?",
                    Long.class, userId.toString(), problemKey, KEPT);
            old.forEach(id -> jdbc.update("delete from code_ground_submissions where id = ?", id));
        } catch (DataAccessException e) {
            log.warn("[CODE_GROUND] old submissions not removed, will be next time: {}", e.getClass().getSimpleName());
        }
    }

    /** Newest first. */
    public List<Submission> of(UUID userId, String problemKey) {
        return jdbc.query("""
                select id, language, code, verdict, passed_count, total_count, created_at from code_ground_submissions
                where user_id = ? and problem_key = ? order by id desc""",
                (rs, n) -> new Submission(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6),
                        rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC).toString()),
                userId.toString(), problemKey);
    }

    /** Every kept submission of the student, newest first, without the code. */
    public List<Entry> all(UUID userId) {
        return jdbc.query("""
                select id, problem_key, language, verdict, passed_count, total_count, created_at from code_ground_submissions
                where user_id = ? order by id desc""",
                (rs, n) -> new Entry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6),
                        rs.getObject(7, LocalDateTime.class)),
                userId.toString());
    }

    /** The code of one submission, only the student's own. */
    public Optional<String> code(UUID userId, long id) {
        return jdbc.queryForList("select code from code_ground_submissions where id = ? and user_id = ?", String.class, id, userId.toString())
                .stream().findFirst();
    }

    /** Gives the bonus for this day once. True when it was given by this call. */
    public boolean giveDailyBonus(UUID userId, LocalDate day, String problemKey, int points) {
        return jdbc.update("insert ignore into code_ground_daily (user_id, day, problem_key, points, solved_at) values (?, ?, ?, ?, utc_timestamp())",
                userId.toString(), day, problemKey, points) == 1;
    }

    public List<Daily> dailyOf(UUID userId) {
        return jdbc.query("select day, problem_key from code_ground_daily where user_id = ?",
                (rs, n) -> new Daily(rs.getObject(1, LocalDate.class), rs.getString(2)), userId.toString());
    }
}
