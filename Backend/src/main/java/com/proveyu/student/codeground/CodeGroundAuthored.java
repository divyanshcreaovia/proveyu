package com.proveyu.student.codeground;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

/** The tables behind admin-managed content and the problem-of-the-day schedule. */
@Repository
@RequiredArgsConstructor
public class CodeGroundAuthored {

    public record TopicRow(String key, String title, String summary, String guide) {}

    public record ProblemRow(String key, String topicKey, String spec, String publishedSpec) {}

    public record Scheduled(String problemKey, boolean auto) {}

    private final JdbcTemplate jdbc;

    public List<TopicRow> topics() {
        return jdbc.query("select topic_key, title, summary, guide from code_ground_topics order by created_at, topic_key",
                (rs, n) -> new TopicRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public void saveTopic(String key, String title, String summary, String guide) {
        jdbc.update("""
                insert into code_ground_topics (topic_key, title, summary, guide) values (?, ?, ?, ?)
                on duplicate key update title = ?, summary = ?, guide = ?""", key, title, summary, guide, title, summary, guide);
    }

    public void deleteTopic(String key) {
        jdbc.update("delete from code_ground_topics where topic_key = ?", key);
    }

    public List<ProblemRow> problems() {
        return jdbc.query("select problem_key, topic_key, spec, published_spec from code_ground_problems order by created_at, problem_key",
                (rs, n) -> new ProblemRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public Optional<ProblemRow> problem(String key) {
        return jdbc.query("select problem_key, topic_key, spec, published_spec from code_ground_problems where problem_key = ?",
                (rs, n) -> new ProblemRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), key).stream().findFirst();
    }

    public void saveProblem(String key, String topicKey, String spec) {
        jdbc.update("""
                insert into code_ground_problems (problem_key, topic_key, spec) values (?, ?, ?)
                on duplicate key update topic_key = ?, spec = ?""", key, topicKey, spec, topicKey, spec);
    }

    /** publishedSpec null = unpublish. */
    public void publish(String key, String publishedSpec) {
        jdbc.update("update code_ground_problems set published_spec = ? where problem_key = ?", publishedSpec, key);
    }

    public void deleteProblem(String key) {
        jdbc.update("delete from code_ground_problems where problem_key = ?", key);
    }

    /** True when a student has run, submitted or saved code for this problem. */
    public boolean used(String key) {
        Integer n = jdbc.queryForObject("""
                select exists(select 1 from code_ground_progress where item_type = 'PROBLEM' and item_key = ?)
                    or exists(select 1 from code_ground_submissions where problem_key = ?)
                    or exists(select 1 from code_ground_drafts where problem_key = ?)
                    or exists(select 1 from code_ground_daily where problem_key = ?)""", Integer.class, key, key, key, key);
        return n != null && n > 0;
    }

    public Set<String> hidden() {
        return new HashSet<>(jdbc.queryForList("select problem_key from code_ground_hidden", String.class));
    }

    public void setHidden(String key, boolean hidden) {
        if (hidden) jdbc.update("insert ignore into code_ground_hidden (problem_key) values (?)", key);
        else jdbc.update("delete from code_ground_hidden where problem_key = ?", key);
    }

    public Map<LocalDate, Scheduled> schedule(LocalDate from, LocalDate to) {
        Map<LocalDate, Scheduled> days = new HashMap<>();
        jdbc.query("select day, problem_key, auto from code_ground_schedule where day between ? and ?",
                rs -> {
                    days.put(rs.getObject(1, LocalDate.class), new Scheduled(rs.getString(2), rs.getBoolean(3)));
                }, from, to);
        return days;
    }

    public void schedule(LocalDate day, String problemKey) {
        jdbc.update("""
                insert into code_ground_schedule (day, problem_key, auto) values (?, ?, 0)
                on duplicate key update problem_key = ?, auto = 0""", day, problemKey, problemKey);
    }

    /** Records an automatic choice unless the day already has one; replace overwrites a choice that is no longer available. */
    public void scheduleAuto(LocalDate day, String problemKey, boolean replace) {
        if (replace) {
            jdbc.update("""
                    insert into code_ground_schedule (day, problem_key, auto) values (?, ?, 1)
                    on duplicate key update problem_key = ?, auto = 1""", day, problemKey, problemKey);
        } else {
            jdbc.update("insert ignore into code_ground_schedule (day, problem_key, auto) values (?, ?, 1)", day, problemKey);
        }
    }

    public void unschedule(LocalDate day) {
        jdbc.update("delete from code_ground_schedule where day = ?", day);
    }

    public int bonusesGiven(LocalDate day) {
        Integer n = jdbc.queryForObject("select count(*) from code_ground_daily where day = ?", Integer.class, day);
        return n == null ? 0 : n;
    }
}
