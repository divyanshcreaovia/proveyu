package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Does one college ever see another's numbers, for EVERY metric, split, batch and branch filter and every student list?
 * <p>
 * College ALPHA has small hand-made data with known answers in every table the metrics read (interviews, announcements, off-campus,
 * proctoring, mock tests, AI interviews, Code ground, documents...). Other colleges (BRAVO with a lot of random data that uses the same
 * branch codes, CHARLIE with odd data: no branches, no genders, no CGPA, offers without a package; DELTA with nothing at all) are added
 * around it. Every number ALPHA gets, and every list of its students, must be exactly the same before and after the others exist.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CxoIsolationIT extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired MetricCatalog catalog;
    @Autowired QueryEngine engine;

    UUID alpha, bravo, charlie, delta;
    Map<String, String> alphaBefore, bravoBefore;

    // ------------------------------------------------------------------ seeding

    UUID newCollege() throws Exception {
        List<String> before = jdbc.queryForList("select college_id from college_profiles", String.class);
        registerCollege("iso");
        List<String> after = jdbc.queryForList("select college_id from college_profiles", String.class);
        after.removeAll(before);
        UUID id = UUID.fromString(after.get(0));
        for (String t : List.of("college_branches", "college_batches", "college_tiers")) jdbc.update("delete from " + t + " where college_id = ?", id.toString());
        return id;
    }

    void seed(Consumer<JdbcTemplate> body) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
            JdbcTemplate t = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
            t.execute("SET FOREIGN_KEY_CHECKS=0");
            body.accept(t);
            t.execute("SET FOREIGN_KEY_CHECKS=1");
            return null;
        });
    }

    static String id() { return UUID.randomUUID().toString(); }

    String branch(JdbcTemplate t, UUID col, String code) {
        List<String> have = t.queryForList("select id from college_branches where college_id = ? and code = ?", String.class, col.toString(), code);
        if (!have.isEmpty()) return have.get(0);
        String id = id();
        t.update("insert into college_branches (id, college_id, code, name) values (?,?,?,?)", id, col.toString(), code, code + " dept");
        return id;
    }

    String student(JdbcTemplate t, UUID col, String tag, int n, String branch, String gender, Double cgpa, String status, int batch, String userId, boolean deleted) {
        String id = id();
        t.update("insert into college_students (id, college_id, user_id, roll_number, first_name, last_name, email, gender, branch_id, batch_year, cgpa, status, deleted_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?)", id, col.toString(), userId, tag + "-" + n, tag + "Name" + n, "Last", tag + n + "@iso.test", gender, branch, batch, cgpa,
                status, deleted ? java.sql.Timestamp.valueOf("2026-01-01 00:00:00") : null);
        return id;
    }

    String company(JdbcTemplate t, UUID col, String name, boolean deleted) {
        String id = id();
        t.update("insert into college_companies (id, college_id, name, deleted_at) values (?,?,?,?)", id, col.toString(), name, deleted ? java.sql.Timestamp.valueOf("2026-01-01 00:00:00") : null);
        return id;
    }

    void offer(JdbcTemplate t, UUID col, String student, String company, String status, Double ctc, String date) {
        t.update("insert into college_offers (id, college_id, student_id, company_id, role_title, ctc_lpa, status, offer_date) values (?,?,?,?,?,?,?,?)", id(), col.toString(), student, company,
                "SDE", ctc == null ? 0 : ctc, status, date);
    }

    // ALPHA: small, hand-made, every answer known -------------------------------------------------------------------------------------------
    String s1, s2, s3, s4, s5, s6;

    void seedAlpha() {
        String c = alpha.toString();
        seed(t -> {
            String comp = branch(t, alpha, "COMP"), it = branch(t, alpha, "IT");
            t.update("insert into college_batches (id, college_id, grad_year, label) values (?,?,?,?)", id(), c, 2025, "2025");
            t.update("insert into college_batches (id, college_id, grad_year, label) values (?,?,?,?)", id(), c, 2026, "2026");
            s1 = student(t, alpha, "ALPHA", 1, comp, "Female", 9.1, "ACTIVE", 2025, "u-a1", false);
            s2 = student(t, alpha, "ALPHA", 2, comp, "Male", 7.5, "PLACED", 2025, "u-a2", false);
            s3 = student(t, alpha, "ALPHA", 3, it, "Female", 6.5, "ACTIVE", 2026, "u-a3", false);
            s4 = student(t, alpha, "ALPHA", 4, it, null, null, "DEBARRED", 2026, "u-a4", false);
            s5 = student(t, alpha, "ALPHA", 5, null, "Male", 8.0, "OPTED_OUT", 2026, "u-a5", false);
            s6 = student(t, alpha, "ALPHA", 6, comp, "Male", 5.5, "ACTIVE", 2026, "u-a6", true); // deleted: counted nowhere
            String acme = company(t, alpha, "ALPHA Acme", false);
            offer(t, alpha, s2, acme, "ACCEPTED", 10.0, "2025-09-10");
            offer(t, alpha, s1, acme, "JOINED", 6.0, "2025-10-02");
            offer(t, alpha, s3, acme, "OFFERED", 8.0, "2026-02-01");
            // interviews: 4 slots (one status each, one repeated)
            for (String[] sl : new String[][]{{"SCHEDULED", "2026-03-01 10:00:00"}, {"COMPLETED", "2026-03-02 10:00:00"}, {"COMPLETED", "2026-04-02 10:00:00"}, {"CANCELLED", "2026-04-03 10:00:00"}}) {
                t.update("insert into college_interview_slots (id, college_id, drive_id, application_id, starts_at, ends_at, status) values (?,?,?,?,?,?,?)", id(), c, id(), id(), sl[1],
                        sl[1].replace("10:00", "11:00"), sl[0]);
            }
            // announcements: 2, with 5 reachable recipients (3 read) and one deleted student's row that must not count
            String an1 = id(), an2 = id();
            t.update("insert into college_announcements (id, college_id, title, body, created_at) values (?,?,?,?,?)", an1, c, "ALPHA notice one", "b", "2026-01-05 09:00:00");
            t.update("insert into college_announcements (id, college_id, title, body, created_at) values (?,?,?,?,?)", an2, c, "ALPHA notice two", "b", "2026-02-10 09:00:00");
            String[][] rec = {{an1, s1, "r"}, {an1, s2, "u"}, {an1, s3, "r"}, {an2, s1, "u"}, {an2, s4, "r"}, {an2, s6, "r"}};
            for (String[] r : rec) t.update("insert into college_announcement_recipients (id, college_id, announcement_id, student_id, read_at) values (?,?,?,?,?)", id(), c, r[0], r[1],
                    r[2].equals("r") ? "2026-02-11 09:00:00" : null);
            // off-campus: 3 applied (2 + 1), one not applied (applied_at null), one from the deleted student
            String j1 = id(), j2 = id();
            for (Object[] j : new Object[][]{{j1, "ALPHA Corp", "Dev"}, {j2, "ALPHA Soft", "QA"}})
                t.update("insert into off_campus_jobs (id, company_name, role_title, job_type, work_mode, apply_url) values (?,?,?,?,?,?)", j[0], j[1], j[2], "FULL_TIME", "ONSITE", "http://x");
            String[][] oc = {{j1, s1, "u-a1", "APPLIED", "2026-03-01 10:00:00"}, {j1, s2, "u-a2", "INTERVIEW", "2026-03-02 10:00:00"}, {j2, s3, "u-a3", "APPLIED", "2026-03-03 10:00:00"},
                    {j2, s4, "u-a4", "APPLIED", null}, {j2, s6, "u-a6", "APPLIED", "2026-03-04 10:00:00"}};
            for (String[] a : oc) t.update("insert into off_campus_applications (id, job_id, student_id, user_id, college_id, status, applied_at) values (?,?,?,?,?,?,?)", id(), a[0], a[1], a[2], c, a[3], a[4]);
            // tests: attempts of s1 (40/50) and s2 (30/60) submitted, s3 not submitted, deleted s6 submitted
            String test = id();
            t.update("insert into prep_tests (id, college_id, title, duration_minutes, definition_json) values (?,?,?,?,?)", test, c, "ALPHA Aptitude", 30, "{}");
            String a1 = id(), a2 = id(), a3 = id(), a4 = id();
            Object[][] at = {{a1, "u-a1", "2026-03-05 10:00:00", 40, 50}, {a2, "u-a2", "2026-03-06 10:00:00", 30, 60}, {a3, "u-a3", null, null, 0}, {a4, "u-a6", "2026-03-07 10:00:00", 50, 50}};
            for (Object[] a : at) t.update("insert into prep_attempts (id, test_id, user_id, started_at, deadline_at, submitted_at, score, max_score) values (?,?,?,?,?,?,?,?)", a[0], test, a[1],
                    "2026-03-05 09:00:00", "2026-03-05 11:00:00", a[2], a[3], a[4]);
            Object[][] ev = {{a1, "u-a1", "TAB_SWITCH"}, {a1, "u-a1", "TAB_SWITCH"}, {a1, "u-a1", "NO_FACE"}, {a2, "u-a2", "TAB_SWITCH"}, {a3, "u-a3", "LOOKING_AWAY"}, {a4, "u-a6", "TAB_SWITCH"}};
            int seq = 0;
            for (Object[] e : ev) t.update("insert into prep_proctor_events (id, attempt_id, user_id, kind, seq, occurred_at) values (?,?,?,?,?,?)", id(), e[0], e[1], e[2], ++seq, "2026-03-05 09:30:00");
            // AI interviews: 3 of non-deleted students (two with a report), one of the deleted student
            String[][] ai = {{s1, "u-a1", "HARD", "70"}, {s2, "u-a2", "EASY", "90"}, {s3, "u-a3", "HARD", null}, {s6, "u-a6", "EASY", "99"}};
            for (String[] x : ai) {
                String sid = id();
                t.update("insert into ai_interview_sessions (id, college_id, student_id, user_id, interview_type, difficulty, role_title, status, planned_questions, credit_source, consent_at, started_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?)", sid, c, x[0], x[1], "TECHNICAL", x[2], "SDE", "COMPLETED", 5, "FREE", "2026-03-05 09:00:00", "2026-03-05 09:00:00");
                if (x[3] != null) t.update("insert into ai_interview_reports (id, session_id, overall_score, recommendation, model) values (?,?,?,?,?)", id(), sid, x[3], "READY", "m");
            }
            // Code ground: u-a1 three submissions (2 accepted), u-a2 one wrong, u-a3 one accepted, deleted student's one accepted
            Object[][] cg = {{"u-a1", "python", "ACCEPTED", "2026-03-01 12:00:00"}, {"u-a1", "java", "WRONG_ANSWER", "2026-03-02 12:00:00"}, {"u-a1", "python", "ACCEPTED", "2026-03-03 12:00:00"},
                    {"u-a2", "cpp", "WRONG_ANSWER", "2026-03-04 12:00:00"}, {"u-a3", "python", "ACCEPTED", "2026-03-05 12:00:00"}, {"u-a6", "python", "ACCEPTED", "2026-03-06 12:00:00"}};
            for (Object[] x : cg) t.update("insert into code_ground_submissions (user_id, problem_key, language, code, verdict, passed_count, total_count, created_at) values (?,?,?,?,?,?,?,?)", x[0], "p1",
                    x[1], "code", x[2], 1, 1, x[3]);
            Object[][] pr = {{"u-a1", "PROBLEM", "p1", "2026-03-01 12:00:00"}, {"u-a1", "PROBLEM", "p2", "2026-03-03 12:00:00"}, {"u-a2", "PROBLEM", "p1", "2026-03-04 12:00:00"},
                    {"u-a3", "PROBLEM", "p1", null}, {"u-a1", "GUIDE", "g1", "2026-03-01 12:00:00"}, {"u-a6", "PROBLEM", "p1", "2026-03-06 12:00:00"}};
            for (Object[] x : pr) t.update("insert into code_ground_progress (user_id, item_type, item_key, done_at) values (?,?,?,?)", x[0], x[1], x[2], x[3]);
            // documents
            Object[][] docs = {{s1, "RESUME", "VERIFIED"}, {s1, "MARKSHEET_10", "PENDING"}, {s2, "RESUME", "PENDING"}, {s6, "RESUME", "VERIFIED"}};
            for (Object[] d : docs) t.update("insert into college_student_documents (id, college_id, student_id, doc_type, file_id, verification_status) values (?,?,?,?,?,?)", id(), c, d[0], d[1], id(), d[2]);
        });
    }

    // BRAVO / ALPHA-extra: a lot of random data in every table, with the same branch codes as ALPHA --------------------------------------------------
    void seedRandom(UUID col, String tag, int students, long seedValue, boolean weird) {
        Random rnd = new Random(seedValue);
        String c = col.toString();
        seed(t -> {
            List<String> branches = new ArrayList<>();
            if (!weird) for (String code : List.of("COMP", "IT", "MECH")) branches.add(branch(t, col, code));
            t.update("insert ignore into college_batches (id, college_id, grad_year, label) values (?,?,?,?)", id(), c, 2025, "2025");
            t.update("insert ignore into college_batches (id, college_id, grad_year, label) values (?,?,?,?)", id(), c, 2026, "2026");
            if (!weird) t.update("insert ignore into college_batches (id, college_id, grad_year, label) values (?,?,?,?)", id(), c, 2027, "2027");
            t.update("insert ignore into college_tiers (id, college_id, name, tier_rank, min_ctc_lpa) values (?,?,?,?,?)", id(), c, tag + " tier", 1, 3);
            List<String> companies = new ArrayList<>();
            for (int i = 0; i < 8; i++) companies.add(company(t, col, tag + " Company " + i, weird && i == 0));
            String[] statuses = {"ACTIVE", "PLACED", "DEBARRED", "OPTED_OUT"};
            String[] offerStatuses = {"OFFERED", "ACCEPTED", "DECLINED", "JOINED", "REVOKED"};
            String test = id();
            t.update("insert into prep_tests (id, college_id, title, duration_minutes, definition_json) values (?,?,?,?,?)", test, c, tag + " Test", 30, "{}");
            String job = id();
            t.update("insert into off_campus_jobs (id, company_name, role_title, job_type, work_mode, apply_url) values (?,?,?,?,?,?)", job, tag + " Job Co", "Dev", "FULL_TIME", "ONSITE", "http://x");
            String an = id();
            t.update("insert into college_announcements (id, college_id, title, body, created_at) values (?,?,?,?,?)", an, c, tag + " announcement", "b", "2026-03-01 09:00:00");
            String drive = id();
            t.update("insert into college_drives (id, college_id, company_id, title, role_title, ctc_lpa, drive_date, status) values (?,?,?,?,?,?,?,?)", drive, c, companies.get(1), tag + " drive", "SDE",
                    12, "2026-03-15", "COMPLETED");
            String round = id();
            t.update("insert into college_drive_rounds (id, college_id, drive_id, seq, name, round_type, scheduled_at) values (?,?,?,?,?,?,?)", round, c, drive, 1, tag + " round", "APTITUDE", "2026-03-16 10:00:00");
            for (int n = 1; n <= students; n++) {
                String user = tag + "-user-" + n;
                String sid = student(t, col, tag, n, branches.isEmpty() || rnd.nextInt(8) == 0 ? null : branches.get(rnd.nextInt(branches.size())),
                        weird || rnd.nextInt(6) == 0 ? null : (rnd.nextBoolean() ? "Male" : "Female"), weird || rnd.nextInt(5) == 0 ? null : 5 + rnd.nextInt(50) / 10.0,
                        statuses[rnd.nextInt(statuses.length)], weird ? 2026 : 2025 + rnd.nextInt(3), user, rnd.nextInt(12) == 0);
                for (int k = rnd.nextInt(3); k > 0; k--)
                    offer(t, col, sid, companies.get(rnd.nextInt(companies.size())), offerStatuses[rnd.nextInt(offerStatuses.length)], weird && rnd.nextBoolean() ? null : 3 + rnd.nextInt(300) / 10.0,
                            "2026-0" + (1 + rnd.nextInt(9)) + "-1" + rnd.nextInt(9));
                String app = id();
                t.update("insert into college_applications (id, college_id, drive_id, student_id, status, created_at) values (?,?,?,?,?,?)", app, c, drive, sid,
                        new String[]{"APPLIED", "SHORTLISTED", "SELECTED", "REJECTED", "WITHDRAWN"}[rnd.nextInt(5)], "2026-03-0" + (1 + rnd.nextInt(8)) + " 10:00:00");
                t.update("insert into college_round_results (id, college_id, round_id, application_id, result) values (?,?,?,?,?)", id(), c, round, app, new String[]{"PASS", "FAIL", "ABSENT", "PENDING"}[rnd.nextInt(4)]);
                t.update("insert into college_interview_slots (id, college_id, drive_id, application_id, starts_at, ends_at, status) values (?,?,?,?,?,?,?)", id(), c, drive, app, "2026-03-20 10:00:00", "2026-03-20 11:00:00",
                        new String[]{"SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"}[rnd.nextInt(4)]);
                String att = id();
                t.update("insert into prep_attempts (id, test_id, user_id, started_at, deadline_at, submitted_at, score, max_score) values (?,?,?,?,?,?,?,?)", att, test, user, "2026-03-05 09:00:00",
                        "2026-03-05 11:00:00", "2026-03-05 10:00:00", rnd.nextInt(100), 100);
                for (int k = rnd.nextInt(4); k > 0; k--)
                    t.update("insert into prep_proctor_events (id, attempt_id, user_id, kind, seq, occurred_at) values (?,?,?,?,?,?)", id(), att, user, new String[]{"TAB_SWITCH", "NO_FACE"}[rnd.nextInt(2)], k, "2026-03-05 09:30:00");
                String sess = id();
                t.update("insert into ai_interview_sessions (id, college_id, student_id, user_id, interview_type, difficulty, role_title, status, planned_questions, credit_source, consent_at, started_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?)", sess, c, sid, user, "HR", new String[]{"EASY", "MEDIUM", "HARD"}[rnd.nextInt(3)], "SDE", "COMPLETED", 5, "FREE", "2026-03-05 09:00:00", "2026-03-05 09:00:00");
                t.update("insert into ai_interview_reports (id, session_id, overall_score, recommendation, model) values (?,?,?,?,?)", id(), sess, 30 + rnd.nextInt(70), "READY", "m");
                t.update("insert into off_campus_applications (id, job_id, student_id, user_id, college_id, status, applied_at) values (?,?,?,?,?,?,?)", id(), job, sid, user, c, "APPLIED", "2026-03-03 10:00:00");
                t.update("insert into college_announcement_recipients (id, college_id, announcement_id, student_id, read_at) values (?,?,?,?,?)", id(), c, an, sid, rnd.nextBoolean() ? "2026-03-02 09:00:00" : null);
                for (int k = rnd.nextInt(4); k > 0; k--)
                    t.update("insert into code_ground_submissions (user_id, problem_key, language, code, verdict, passed_count, total_count, created_at) values (?,?,?,?,?,?,?,?)", user, "p" + k, "python", "x",
                            rnd.nextBoolean() ? "ACCEPTED" : "WRONG_ANSWER", 1, 1, "2026-03-04 12:00:00");
                if (rnd.nextBoolean()) t.update("insert into code_ground_progress (user_id, item_type, item_key, done_at) values (?,?,?,?)", user, "PROBLEM", "p1", "2026-03-04 12:00:00");
                t.update("insert into college_student_documents (id, college_id, student_id, doc_type, file_id, verification_status) values (?,?,?,?,?,?)", id(), c, sid, "RESUME", id(), rnd.nextBoolean() ? "VERIFIED" : "PENDING");
            }
        });
    }

    // ------------------------------------------------------------------ snapshots: every metric, split, batch and branch filter, and the student lists

    Map<String, String> snapshot(UUID col) {
        Map<String, String> out = new TreeMap<>();
        for (MetricCatalog.Def d : catalog.all()) {
            for (Dimension dim : catalog.dimensions(d)) {
                for (Integer b : new Integer[]{null, 2025, 2026, 2099}) {
                    for (String br : new String[]{null, "COMP", "ZZ"}) {
                        WidgetSpec w = new WidgetSpec("w", "t", dim == Dimension.NONE ? WidgetType.KPI : WidgetType.BAR, d.key(), dim, b, br, null, null, null, "md");
                        WidgetData r = engine.run(w, col);
                        String key = d.key() + "|" + dim + "|" + b + "|" + br;
                        out.put(key, r.value() + "|" + r.labels() + "|" + r.values());
                        if (catalog.drillable(d) && b == null && br == null) {
                            for (String label : dim == Dimension.NONE ? java.util.Collections.<String>singletonList(null) : r.labels().subList(0, Math.min(4, r.labels().size()))) {
                                out.put("drill|" + key + "|" + label, engine.drill(w, label, col, 200).rows().stream().map(x -> x.roll() + "/" + x.name() + "/" + x.branch() + "/" + x.cgpa() + "/" + x.placed())
                                        .sorted().toList().toString());
                            }
                        }
                    }
                }
                if (dim == Dimension.BATCH || dim == Dimension.YEAR) {
                    WidgetSpec w = new WidgetSpec("w", "t", WidgetType.BAR, d.key(), dim, null, null, null, null, null, "md", List.of(2025, 2026, 2099));
                    WidgetData r = engine.run(w, col);
                    out.put(d.key() + "|" + dim + "|years", r.labels() + "|" + r.values());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the tests, in order

    @BeforeAll
    void setUp() throws Exception {
        alpha = newCollege();
        seedAlpha();
    }

    /** The test seeds rows without their parents (foreign keys off); they are removed so other tests sharing the database never see them. */
    @AfterAll
    void cleanUp() {
        List<String> ids = new ArrayList<>();
        for (UUID u : new UUID[]{alpha, bravo, charlie, delta}) if (u != null) ids.add("'" + u + "'");
        String in = String.join(",", ids);
        String users = "(user_id like 'u-a%' or user_id like '%-user-%')";
        seed(t -> {
            t.update("delete from prep_proctor_events where " + users);
            t.update("delete from prep_attempts where " + users);
            t.update("delete from prep_tests where college_id in (" + in + ")");
            t.update("delete from ai_interview_reports where session_id in (select id from ai_interview_sessions where college_id in (" + in + "))");
            t.update("delete from ai_interview_sessions where college_id in (" + in + ")");
            t.update("delete from off_campus_applications where college_id in (" + in + ")");
            t.update("delete from off_campus_jobs where company_name like 'ALPHA %' or company_name like '%Job Co'");
            t.update("delete from code_ground_submissions where " + users);
            t.update("delete from code_ground_progress where " + users);
            for (String table : List.of("college_interview_slots", "college_announcement_recipients", "college_announcements", "college_student_documents", "college_round_results",
                    "college_drive_rounds", "college_applications", "college_offers", "college_drives", "college_students", "college_companies"))
                t.update("delete from " + table + " where college_id in (" + in + ")");
        });
    }

    WidgetData run(UUID col, String metric, Dimension dim, Integer batch) {
        return engine.run(new WidgetSpec("w", "t", dim == Dimension.NONE ? WidgetType.KPI : WidgetType.BAR, metric, dim, batch, null, null, null, null, "md"), col);
    }

    java.util.Map<String, Double> byLabel(WidgetData d) {
        java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < d.labels().size(); i++) m.put(d.labels().get(i), d.values().get(i));
        return m;
    }

    @Test @Order(1)
    void alphaNumbersAreExactlyWhatWeSeeded() {
        // students and who is counted (the deleted one never is)
        assertThat(run(alpha, "students_total", Dimension.NONE, null).value()).isEqualTo(5);
        assertThat(byLabel(run(alpha, "students_total", Dimension.GENDER, null))).containsEntry("Female", 2.0).containsEntry("Male", 2.0).containsEntry("Unknown", 1.0);
        assertThat(byLabel(run(alpha, "students_total", Dimension.CGPA_BAND, null))).containsEntry("9 and above", 1.0).containsEntry("7-8", 1.0).containsEntry("6-7", 1.0)
                .containsEntry("8-9", 1.0).containsEntry("No CGPA", 1.0);
        assertThat(byLabel(run(alpha, "students_total", Dimension.BRANCH, null))).containsEntry("COMP", 2.0).containsEntry("IT", 2.0).containsEntry("Unknown", 1.0);
        // interviews
        assertThat(run(alpha, "interviews", Dimension.NONE, null).value()).isEqualTo(4);
        assertThat(byLabel(run(alpha, "interviews", Dimension.STATUS, null))).containsEntry("COMPLETED", 2.0).containsEntry("SCHEDULED", 1.0).containsEntry("CANCELLED", 1.0);
        // announcements: 2 sent; 5 reachable rows (the deleted student's does not count), 3 read
        assertThat(run(alpha, "announcements", Dimension.NONE, null).value()).isEqualTo(2);
        assertThat(run(alpha, "announcement_read_rate", Dimension.NONE, null).value()).isEqualTo(60.0);
        assertThat(byLabel(run(alpha, "announcement_read_rate", Dimension.BRANCH, null))).containsEntry("COMP", 33.3).containsEntry("IT", 100.0);
        // off-campus
        assertThat(run(alpha, "offcampus_applications", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(byLabel(run(alpha, "offcampus_applications", Dimension.STATUS, null))).containsEntry("APPLIED", 2.0).containsEntry("INTERVIEW", 1.0);
        assertThat(byLabel(run(alpha, "offcampus_applications", Dimension.COMPANY, null))).containsEntry("ALPHA Corp", 2.0).containsEntry("ALPHA Soft", 1.0);
        // mock tests and proctoring
        assertThat(run(alpha, "mock_test_attempts", Dimension.NONE, null).value()).isEqualTo(2);
        assertThat(run(alpha, "mock_test_avg_score", Dimension.NONE, null).value()).isEqualTo(65.0); // (80% + 50%) / 2
        assertThat(run(alpha, "proctoring_flags", Dimension.NONE, null).value()).isEqualTo(5);
        assertThat(byLabel(run(alpha, "proctoring_flags", Dimension.TYPE, null))).containsEntry("TAB_SWITCH", 3.0).containsEntry("NO_FACE", 1.0).containsEntry("LOOKING_AWAY", 1.0);
        assertThat(run(alpha, "flagged_attempts", Dimension.NONE, null).value()).isEqualTo(3);
        // AI interviews
        assertThat(run(alpha, "ai_interviews", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(run(alpha, "ai_interview_score", Dimension.NONE, null).value()).isEqualTo(80.0);
        assertThat(byLabel(run(alpha, "ai_interview_score", Dimension.DIFFICULTY, null))).containsEntry("HARD", 70.0).containsEntry("EASY", 90.0);
        // Code ground
        assertThat(run(alpha, "code_submissions", Dimension.NONE, null).value()).isEqualTo(5);
        assertThat(run(alpha, "code_accept_rate", Dimension.NONE, null).value()).isEqualTo(60.0);
        assertThat(run(alpha, "code_active_coders", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(run(alpha, "code_problems_solved", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(byLabel(run(alpha, "code_submissions", Dimension.LANGUAGE, null))).containsEntry("python", 3.0).containsEntry("java", 1.0).containsEntry("cpp", 1.0);
        assertThat(byLabel(run(alpha, "code_problems_solved", Dimension.BRANCH, null))).containsEntry("COMP", 3.0);
        assertThat(byLabel(run(alpha, "code_submissions", Dimension.BATCH, null))).containsEntry("2025", 4.0).containsEntry("2026", 1.0);
        // documents
        assertThat(run(alpha, "student_documents", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(byLabel(run(alpha, "student_documents", Dimension.STATUS, null))).containsEntry("PENDING", 2.0).containsEntry("VERIFIED", 1.0);
        // offers
        assertThat(run(alpha, "offers", Dimension.NONE, null).value()).isEqualTo(3);
        assertThat(run(alpha, "median_ctc", Dimension.NONE, null).value()).isEqualTo(8.0);
        assertThat(run(alpha, "placed_students", Dimension.NONE, null).value()).isEqualTo(2);
    }

    @Test @Order(2)
    void alphaIsTheSameBeforeAndAfterOtherCollegesExist() throws Exception {
        alphaBefore = snapshot(alpha);
        assertThat(alphaBefore.size()).isGreaterThan(2000);
        bravo = newCollege();
        seedRandom(bravo, "BRAVO", 60, 42L, false);
        charlie = newCollege();
        seedRandom(charlie, "CHARLIE", 25, 7L, true);
        delta = newCollege(); // nothing at all
        Map<String, String> after = snapshot(alpha);
        assertThat(after.keySet()).isEqualTo(alphaBefore.keySet());
        List<String> changed = new ArrayList<>();
        for (var e : alphaBefore.entrySet()) if (!e.getValue().equals(after.get(e.getKey()))) changed.add(e.getKey() + " : " + e.getValue() + " -> " + after.get(e.getKey()));
        assertThat(changed).as("ALPHA's numbers or student lists changed when other colleges were added").isEmpty();
        // and nothing of the others is in any of ALPHA's labels or lists
        assertThat(after.values().stream().filter(v -> v.contains("BRAVO") || v.contains("CHARLIE")).toList()).isEmpty();
        // the others are really there (so the check above could have failed)
        assertThat(run(bravo, "students_total", Dimension.NONE, null).value()).isGreaterThan(30);
        assertThat(run(bravo, "offers", Dimension.NONE, null).value()).isGreaterThan(20);
        assertThat(run(bravo, "code_submissions", Dimension.NONE, null).value()).isGreaterThan(20);
    }

    @Test @Order(3)
    void bravoIsTheSameBeforeAndAfterMoreDataIsAddedToAlpha() {
        bravoBefore = snapshot(bravo);
        assertThat(bravoBefore.values().stream().filter(v -> v.contains("ALPHA") || v.contains("CHARLIE")).toList()).isEmpty();
        seedRandom(alpha, "ALPHAX", 40, 99L, false); // a lot more ALPHA data, with the same branch codes
        Map<String, String> after = snapshot(bravo);
        List<String> changed = new ArrayList<>();
        for (var e : bravoBefore.entrySet()) if (!e.getValue().equals(after.get(e.getKey()))) changed.add(e.getKey());
        assertThat(changed).as("BRAVO's numbers changed when ALPHA got more data").isEmpty();
    }

    @Test @Order(4)
    void oddAndEmptyCollegesNeverFailAndShowNothingInvented() {
        Map<String, String> empty = snapshot(delta);
        for (var e : empty.entrySet()) {
            String v = e.getValue();
            if (e.getKey().startsWith("drill|")) { assertThat(v).as(e.getKey()).isEqualTo("[]"); continue; }
            if (e.getKey().endsWith("|years")) { assertThat(v).as(e.getKey()).matches("\\[.*\\]\\|\\[.*\\]"); continue; }
            String[] p = v.split("\\|", 3);
            assertThat(p[0]).as(e.getKey()).isIn("null", "0.0");
            assertThat(p[2].replaceAll("[\\[\\], ]|null|0\\.0", "")).as(e.getKey()).isEmpty(); // every value in the list is 0 or null
        }
        Map<String, String> odd = snapshot(charlie); // no branches, no genders, no CGPA, offers without a package, a deleted company
        assertThat(odd).isNotEmpty();
        assertThat(odd.values().stream().filter(v -> v.contains("ALPHA") || v.contains("BRAVO")).toList()).isEmpty();
        assertThat(run(charlie, "median_ctc", Dimension.NONE, null).value()).isNotNull(); // some offers have a package
        assertThat(byLabel(run(charlie, "students_total", Dimension.GENDER, null))).containsOnlyKeys("Unknown");
        assertThat(byLabel(run(charlie, "students_total", Dimension.CGPA_BAND, null))).containsOnlyKeys("No CGPA");
        assertThat(byLabel(run(charlie, "students_total", Dimension.BRANCH, null))).containsOnlyKeys("Unknown");
    }
}
