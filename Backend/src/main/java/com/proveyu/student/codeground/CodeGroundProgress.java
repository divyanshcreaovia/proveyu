package com.proveyu.student.codeground;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What students have finished in Code ground (table code_ground_progress). Times are stored in UTC. */
@Repository
@RequiredArgsConstructor
public class CodeGroundProgress {

    /** DAILY: a problem-of-the-day bonus (only in a college's list, from its own table). */
    public enum Type { PROBLEM, GUIDE, DAILY }

    /** Keys of the problems and guides a student has finished, and of the problems tried but not solved yet. */
    public record Done(Set<String> problems, Set<String> guides, Set<String> attempted) {}

    /** One finished problem, guide or daily bonus of a student of a college. doneAt is UTC; points is set for DAILY only. */
    public record Finished(UUID userId, String name, String rollNumber, UUID branchId, String branch, int batchYear, Type type, String key,
                           LocalDateTime doneAt, int points) {}

    private final JdbcTemplate jdbc;

    /** Everything the students (not deleted) of one college have finished. Never reads another college. */
    public List<Finished> ofCollege(UUID collegeId) {
        return jdbc.query("""
                select s.user_id, concat_ws(' ', s.first_name, s.last_name), s.roll_number, s.branch_id, b.name, s.batch_year,
                       p.item_type, p.item_key, p.done_at, p.points
                from college_students s
                join (select user_id, item_type, item_key, done_at, 0 as points from code_ground_progress where done_at is not null
                      union all
                      select user_id, 'DAILY', problem_key, solved_at, points from code_ground_daily) p on p.user_id = s.user_id
                left join college_branches b on b.id = s.branch_id
                where s.college_id = ? and s.deleted_at is null""",
                (rs, n) -> new Finished(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                        rs.getString(4) == null ? null : UUID.fromString(rs.getString(4)), rs.getString(5), rs.getInt(6),
                        Type.valueOf(rs.getString(7)), rs.getString(8), rs.getObject(9, LocalDateTime.class), rs.getInt(10)),
                collegeId.toString());
    }

    public Done done(UUID userId) {
        Done done = new Done(new HashSet<>(), new HashSet<>(), new HashSet<>());
        jdbc.query("select item_type, item_key, done_at is not null from code_ground_progress where user_id = ?",
                rs -> {
                    boolean problem = Type.PROBLEM.name().equals(rs.getString(1));
                    if (!rs.getBoolean(3)) {
                        if (problem) done.attempted().add(rs.getString(2));
                    } else {
                        (problem ? done.problems() : done.guides()).add(rs.getString(2));
                    }
                }, userId.toString());
        return done;
    }

    /** When each solved problem was first solved (UTC). */
    public Map<String, LocalDateTime> solvedAt(UUID userId) {
        Map<String, LocalDateTime> at = new HashMap<>();
        jdbc.query("select item_key, done_at from code_ground_progress where user_id = ? and item_type = 'PROBLEM' and done_at is not null",
                rs -> {
                    at.put(rs.getString(1), rs.getObject(2, LocalDateTime.class));
                }, userId.toString());
        return at;
    }

    /** One test run of a problem. Once solved it stays solved; the first solve time is kept. */
    public void attempt(UUID userId, String problemKey, boolean solved) {
        jdbc.update("""
                insert into code_ground_progress (user_id, item_type, item_key, attempts, done_at)
                values (?, 'PROBLEM', ?, 1, if(?, utc_timestamp(), null))
                on duplicate key update attempts = attempts + 1, done_at = coalesce(done_at, if(?, utc_timestamp(), null))""",
                userId.toString(), problemKey, solved, solved);
    }

    public void guideRead(UUID userId, String topicKey) {
        jdbc.update("""
                insert into code_ground_progress (user_id, item_type, item_key, done_at) values (?, 'GUIDE', ?, utc_timestamp())
                on duplicate key update done_at = coalesce(done_at, utc_timestamp())""", userId.toString(), topicKey);
    }
}
