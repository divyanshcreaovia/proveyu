package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.MetricInfo;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.proveyu.cxo.domain.CxoModel.Dimension.*;

/**
 * The semantic layer of the CXO portal: every number a dashboard can show, written once as fixed SQL.
 * <p>
 * The AI only picks a metric key, a dimension from that metric's list and optional filters. {@link #build} turns that into SQL from
 * the fragments below; filter values are always bind parameters and the college id is always the first parameters, taken from the
 * signed-in account. Nothing the model writes is ever concatenated into SQL.
 */
@Component
public class MetricCatalog {

    /** Offers that count as an offer (not declined or revoked). */
    private static final String ACTIVE_OFFER = "('OFFERED','ACCEPTED','JOINED')";
    /** Offers that make a student placed. */
    private static final String PLACED_OFFER = "('ACCEPTED','JOINED')";

    private static final String CTC_BAND_SQL = """
            case when o.ctc_lpa < 3 then 'Below 3 LPA' when o.ctc_lpa < 5 then '3-5 LPA' when o.ctc_lpa < 8 then '5-8 LPA'
                 when o.ctc_lpa < 12 then '8-12 LPA' when o.ctc_lpa < 20 then '12-20 LPA' else '20+ LPA' end""";

    /** A metric: FROM ... WHERE with {@code collegeParams} placeholders for the college id, the value, and label SQL per dimension. */
    record Def(String key, String label, String description, Unit unit, String from, int collegeParams, String value,
               Map<Dimension, String> dims, String dateCol, String batchCol, String branchCol, Map<Dimension, String> sortBy) {}

    /** A ready query: SQL and its bind parameters. */
    public record Query(String sql, List<Object> args, boolean single) {}

    private final Map<String, Def> defs = new LinkedHashMap<>();

    public MetricCatalog() {
        String student = "college_students s left join college_branches b on b.id = s.branch_id";
        String placedSet = "(select distinct student_id from college_offers where college_id = ? and status in " + PLACED_OFFER + ")";
        String offersAll = """
                from college_offers o
                join college_students s on s.id = o.student_id and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                left join college_companies c on c.id = o.company_id
                left join college_tiers t on t.college_id = o.college_id and t.tier_rank = o.tier_rank
                where o.college_id = ?""";
        String offers = offersAll + " and o.status in " + ACTIVE_OFFER;
        String apps = """
                from college_applications a
                join college_students s on s.id = a.student_id and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                left join college_drives d on d.id = a.drive_id
                left join college_companies c on c.id = d.company_id
                where a.college_id = ?""";
        String attempts = """
                from prep_attempts pa
                join college_students s on s.user_id = pa.user_id and s.college_id = ? and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                left join prep_tests pt on pt.id = pa.test_id
                where pa.submitted_at is not null""";
        String sessions = """
                from ai_interview_sessions x
                join college_students s on s.id = x.student_id and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                where x.college_id = ?""";

        Map<Dimension, String> studentDims = dims(BRANCH, "b.code", BATCH, "cast(s.batch_year as char)");

        add(new Def("students_total", "Students", "Students on the college roll (all statuses)", Unit.COUNT,
                "from " + student + " where s.college_id = ? and s.deleted_at is null", 1, "count(*)",
                with(studentDims, STATUS, "s.status"), null, "s.batch_year", "b.code", Map.of()));
        add(new Def("placed_students", "Placed students", "Students with an accepted or joined offer", Unit.COUNT,
                "from " + student + " join " + placedSet + " p on p.student_id = s.id where s.college_id = ? and s.deleted_at is null", 2,
                "count(*)", studentDims, null, "s.batch_year", "b.code", Map.of()));
        add(new Def("placement_rate", "Placement rate", "Placed students as a % of eligible students (not debarred or opted out)", Unit.PERCENT,
                "from " + student + " left join " + placedSet + " p on p.student_id = s.id"
                        + " where s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT')", 2,
                "round(100 * count(p.student_id) / nullif(count(*), 0), 1)", studentDims, null, "s.batch_year", "b.code", Map.of()));
        add(new Def("unplaced_students", "Unplaced students", "Eligible students without an accepted offer yet", Unit.COUNT,
                "from " + student + " left join " + placedSet + " p on p.student_id = s.id"
                        + " where s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT') and p.student_id is null", 2,
                "count(*)", studentDims, null, "s.batch_year", "b.code", Map.of()));

        add(new Def("unplaced_list", "Unplaced students list", "Names of eligible students without an accepted offer, with roll number, "
                + "branch and batch, highest CGPA first (value: CGPA). Use with TABLE", Unit.CGPA,
                "from " + student + " left join " + placedSet + " p on p.student_id = s.id"
                        + " where s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT') and p.student_id is null", 2,
                "max(s.cgpa)", dims(STUDENT, "concat(trim(concat(coalesce(s.first_name, ''), ' ', coalesce(s.last_name, ''))), ' (', "
                        + "coalesce(s.roll_number, '-'), ') · ', coalesce(b.code, '-'), ' · ', coalesce(cast(s.batch_year as char), '-'))"),
                null, "s.batch_year", "b.code", Map.of()));

        Map<Dimension, String> offerDims = dims(MONTH, "date_format(o.offer_date, '%Y-%m')", COMPANY, "c.name", BRANCH, "b.code",
                BATCH, "cast(s.batch_year as char)", TIER, "t.name", STATUS, "o.status");
        add(new Def("offers", "Offers", "Offers made (offered, accepted or joined)", Unit.COUNT, offers, 1, "count(*)",
                offerDims, "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("avg_ctc", "Average CTC", "Average CTC of offers, in LPA", Unit.LPA, offers + " and o.ctc_lpa is not null", 1,
                "round(avg(o.ctc_lpa), 2)", without(offerDims, STATUS), "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("highest_ctc", "Highest CTC", "Highest CTC offered, in LPA", Unit.LPA, offers + " and o.ctc_lpa is not null", 1,
                "max(o.ctc_lpa)", without(offerDims, STATUS), "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("offer_acceptance_rate", "Offer acceptance rate", "Accepted or joined offers as a % of offers students decided on "
                + "(accepted, joined or declined; offers still open are not counted)", Unit.PERCENT,
                offersAll + " and o.status in ('ACCEPTED','JOINED','DECLINED')", 1,
                "round(100 * sum(o.status in ('ACCEPTED','JOINED')) / nullif(count(*), 0), 1)", without(offerDims, STATUS), "o.offer_date",
                "s.batch_year", "b.code", Map.of()));
        add(new Def("offers_by_ctc", "Offers by package", "Offers grouped into CTC bands", Unit.COUNT, offers + " and o.ctc_lpa is not null", 1,
                "count(*)", dims(CTC_BAND, CTC_BAND_SQL), "o.offer_date", "s.batch_year", "b.code", Map.of(CTC_BAND, "min(o.ctc_lpa)")));

        String drives = "from college_drives d left join college_companies c on c.id = d.company_id where d.college_id = ? and d.deleted_at is null";
        Map<Dimension, String> driveDims = dims(STATUS, "d.status", TYPE, "d.drive_type", MONTH, "date_format(d.drive_date, '%Y-%m')", COMPANY, "c.name");
        add(new Def("drives", "Placement drives", "Placement drives (not deleted)", Unit.COUNT, drives, 1, "count(*)",
                driveDims, "d.drive_date", null, null, Map.of()));
        add(new Def("avg_drive_ctc", "Average package on offer", "Average CTC advertised by drives, in LPA", Unit.LPA, drives + " and d.ctc_lpa is not null", 1,
                "round(avg(d.ctc_lpa), 2)", without(driveDims, STATUS), "d.drive_date", null, null, Map.of()));

        Map<Dimension, String> appDims = dims(STATUS, "a.status", MONTH, "date_format(a.created_at, '%Y-%m')", COMPANY, "c.name", BRANCH, "b.code",
                BATCH, "cast(s.batch_year as char)");
        add(new Def("applications", "Applications", "Applications to campus drives", Unit.COUNT, apps, 1, "count(*)",
                appDims, "a.created_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("selection_rate", "Selection rate", "Selected applications as a % of decided ones (selected or rejected)", Unit.PERCENT,
                apps + " and a.status in ('SELECTED','REJECTED')", 1, "round(100 * sum(a.status = 'SELECTED') / nullif(count(*), 0), 1)",
                without(appDims, STATUS), "a.created_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("placement_funnel", "Placement funnel", "Students at each stage: eligible, applied, shortlisted, selected, placed", Unit.COUNT,
                null, 0, null, dims(STAGE, "stage"), null, "s.batch_year", "b.code", Map.of()));

        String rounds = """
                from college_round_results rr
                join college_drive_rounds r on r.id = rr.round_id
                join college_applications a on a.id = rr.application_id
                join college_students s on s.id = a.student_id and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                left join college_drives d on d.id = r.drive_id
                left join college_companies c on c.id = d.company_id
                where rr.college_id = ?""";
        Map<Dimension, String> roundDims = dims(ROUND, "r.round_type", COMPANY, "c.name", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)",
                MONTH, "date_format(r.scheduled_at, '%Y-%m')");
        add(new Def("round_results", "Round results", "Students' results in drive selection rounds (aptitude, coding, GD, technical, HR)",
                Unit.COUNT, rounds, 1, "count(*)", with(roundDims, STATUS, "rr.result"), "r.scheduled_at", "s.batch_year", "b.code",
                Map.of(ROUND, "min(r.seq)")));
        add(new Def("round_pass_rate", "Round pass rate", "Passed as a % of decided results (pass or fail) in drive selection rounds; "
                + "by ROUND shows where students drop off", Unit.PERCENT, rounds + " and rr.result in ('PASS','FAIL')", 1,
                "round(100 * sum(rr.result = 'PASS') / nullif(count(*), 0), 1)", roundDims, "r.scheduled_at", "s.batch_year", "b.code",
                Map.of(ROUND, "min(r.seq)")));

        add(new Def("interviews", "Interviews", "Interview slots scheduled by the placement cell", Unit.COUNT,
                "from college_interview_slots i left join college_drives d on d.id = i.drive_id left join college_companies c on c.id = d.company_id"
                        + " where i.college_id = ?", 1, "count(*)",
                dims(STATUS, "i.status", MONTH, "date_format(i.starts_at, '%Y-%m')", COMPANY, "c.name"), "i.starts_at", null, null, Map.of()));
        add(new Def("companies", "Recruiting companies", "Companies in the college's recruiter list", Unit.COUNT,
                "from college_companies c where c.college_id = ? and c.deleted_at is null", 1, "count(*)",
                dims(STATUS, "c.mou_status"), null, null, null, Map.of()));

        Map<Dimension, String> testDims = dims(MONTH, "date_format(pa.submitted_at, '%Y-%m')", TEST, "pt.title", BRANCH, "b.code",
                BATCH, "cast(s.batch_year as char)");
        add(new Def("mock_test_attempts", "Mock test attempts", "Submitted mock test and college test attempts", Unit.COUNT, attempts, 1,
                "count(*)", testDims, "pa.submitted_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("mock_test_avg_score", "Average test score", "Average score of submitted attempts, in %", Unit.PERCENT,
                attempts + " and pa.score is not null and pa.max_score > 0", 1, "round(avg(100 * pa.score / pa.max_score), 1)",
                testDims, "pa.submitted_at", "s.batch_year", "b.code", Map.of()));

        Map<Dimension, String> aiDims = dims(MONTH, "date_format(x.started_at, '%Y-%m')", TYPE, "x.interview_type", DIFFICULTY, "x.difficulty",
                BRANCH, "b.code", BATCH, "cast(s.batch_year as char)");
        add(new Def("ai_interviews", "AI mock interviews", "AI mock interviews taken by students", Unit.COUNT, sessions, 1, "count(*)",
                with(aiDims, STATUS, "x.status"), "x.started_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("ai_interview_score", "AI interview score", "Average overall score of finished AI interviews (0-100)", Unit.SCORE,
                sessions.replace("where x.college_id = ?", "join ai_interview_reports r on r.session_id = x.id where x.college_id = ?"), 1,
                "round(avg(r.overall_score), 1)", aiDims, "x.started_at", "s.batch_year", "b.code", Map.of()));

        add(new Def("offcampus_applications", "Off-campus applications", "Applications students made to off-campus jobs", Unit.COUNT,
                "from off_campus_applications oc join college_students s on s.id = oc.student_id and s.deleted_at is null"
                        + " left join college_branches b on b.id = s.branch_id left join off_campus_jobs j on j.id = oc.job_id"
                        + " where oc.college_id = ? and oc.applied_at is not null", 1,
                "count(*)", dims(STATUS, "oc.status", MONTH, "date_format(oc.applied_at, '%Y-%m')", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)",
                        COMPANY, "j.company_name", JOB, "concat(j.role_title, ' · ', j.company_name)"),
                "oc.applied_at", "s.batch_year", "b.code", Map.of()));

        String reach = """
                from college_announcement_recipients ar
                join college_announcements an on an.id = ar.announcement_id
                join college_students s on s.id = ar.student_id and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                where ar.college_id = ?""";
        add(new Def("announcements", "Announcements sent", "Announcements the placement cell sent to students", Unit.COUNT,
                "from college_announcements an where an.college_id = ?", 1, "count(*)", dims(MONTH, "date_format(an.created_at, '%Y-%m')"),
                "an.created_at", null, null, Map.of()));
        add(new Def("announcement_read_rate", "Announcement read rate", "Share of students who opened announcements sent to them, in %",
                Unit.PERCENT, reach, 1, "round(100 * sum(ar.read_at is not null) / nullif(count(*), 0), 1)",
                dims(ANNOUNCEMENT, "an.title", MONTH, "date_format(an.created_at, '%Y-%m')", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)"),
                "an.created_at", "s.batch_year", "b.code", Map.of()));

        String proctor = """
                from prep_proctor_events e
                join prep_attempts pa on pa.id = e.attempt_id
                join college_students s on s.user_id = pa.user_id and s.college_id = ? and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                left join prep_tests pt on pt.id = pa.test_id
                where e.kind is not null""";
        Map<Dimension, String> proctorDims = dims(TYPE, "e.kind", TEST, "pt.title", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)",
                MONTH, "date_format(e.occurred_at, '%Y-%m')");
        add(new Def("proctoring_flags", "Proctoring flags", "Integrity flags raised during tests (tab switch, window blur, screen share stopped, "
                + "no face, several faces, looking away, camera blocked, voice)", Unit.COUNT, proctor, 1, "count(*)", proctorDims,
                "e.occurred_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("flagged_attempts", "Flagged test attempts", "Test attempts with at least one proctoring flag", Unit.COUNT, proctor, 1,
                "count(distinct e.attempt_id)", without(proctorDims, TYPE), "e.occurred_at", "s.batch_year", "b.code", Map.of()));
    }

    // ------------------------------------------------------------------ catalog

    public List<MetricInfo> infos() {
        return defs.values().stream().map(d -> new MetricInfo(d.key(), d.label(), d.description(), d.unit(), dimensions(d))).toList();
    }

    public Optional<Def> find(String key) {
        return Optional.ofNullable(key == null ? null : defs.get(key.trim().toLowerCase()));
    }

    /** Dimensions a metric supports; NONE (a single number) first when allowed. */
    public List<Dimension> dimensions(Def d) {
        List<Dimension> out = new ArrayList<>();
        if (d.from() != null && d.unit() != Unit.CGPA) out.add(NONE); // a list (CGPA per student) has no meaningful single number
        out.addAll(d.dims().keySet());
        return out;
    }

    public boolean isFunnel(Def d) {
        return "placement_funnel".equals(d.key());
    }

    // ------------------------------------------------------------------ query building

    /** SQL for one widget. The spec must already be validated against this catalog. */
    public Query build(Def d, WidgetSpec w, UUID collegeId) {
        Dimension dim = w.dimension() == null ? NONE : w.dimension();
        List<Object> args = new ArrayList<>(Collections.nCopies(d.collegeParams(), collegeId.toString()));
        StringBuilder where = new StringBuilder();
        if (w.batchYear() != null && d.batchCol() != null) { where.append(" and ").append(d.batchCol()).append(" = ?"); args.add(w.batchYear()); }
        if (w.branchCode() != null && d.branchCol() != null) { where.append(" and ").append(d.branchCol()).append(" = ?"); args.add(w.branchCode()); }
        LocalDate from = w.from();
        if (dim == MONTH && from == null) from = LocalDate.now().withDayOfMonth(1).minusMonths(11); // a month chart shows the last 12 months
        if (d.dateCol() != null && from != null) { where.append(" and ").append(d.dateCol()).append(" >= ?"); args.add(from); }
        if (d.dateCol() != null && w.to() != null) { where.append(" and ").append(d.dateCol()).append(" < ?"); args.add(w.to().plusDays(1)); }

        if (dim == NONE) return new Query("select " + d.value() + " " + d.from() + where, args, true);

        String label = "coalesce(" + d.dims().get(dim) + ", 'Unknown')";
        if (dim == MONTH) where.append(" and ").append(d.dateCol()).append(" is not null");
        String order = dim == MONTH || dim == BATCH ? "label asc" : d.sortBy().containsKey(dim) ? d.sortBy().get(dim) + " asc" : "v desc, label asc";
        int limit = dim == MONTH ? 24 : dim == STUDENT ? clamp(w.limit(), 3, 50, 50) : clamp(w.limit(), 3, 20, 10);
        args.add(limit);
        return new Query("select " + label + " as label, " + d.value() + " as v " + d.from() + where + " group by label order by " + order + " limit ?",
                args, false);
    }

    /** The placement funnel: one query per stage over the same student filter. */
    public List<Map.Entry<String, Query>> funnel(WidgetSpec w, UUID collegeId) {
        StringBuilder f = new StringBuilder(" and s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT')");
        List<Object> filter = new ArrayList<>(List.of(collegeId.toString()));
        if (w.batchYear() != null) { f.append(" and s.batch_year = ?"); filter.add(w.batchYear()); }
        if (w.branchCode() != null) { f.append(" and b.code = ?"); filter.add(w.branchCode()); }
        String base = " from college_students s left join college_branches b on b.id = s.branch_id";
        String apps = " join college_applications a on a.student_id = s.id";
        String c = collegeId.toString();
        List<Map.Entry<String, Query>> out = new ArrayList<>();
        out.add(Map.entry("Eligible", new Query("select count(*)" + base + " where 1 = 1" + f, new ArrayList<>(filter), true)));
        out.add(Map.entry("Applied", new Query("select count(distinct s.id)" + base + apps + " where a.status <> 'WITHDRAWN'" + f, new ArrayList<>(filter), true)));
        out.add(Map.entry("Shortlisted", new Query("select count(distinct s.id)" + base + apps
                + " where a.status in ('SHORTLISTED','IN_PROCESS','SELECTED')" + f, new ArrayList<>(filter), true)));
        out.add(Map.entry("Selected", new Query("select count(distinct s.id)" + base + apps + " where a.status = 'SELECTED'" + f, new ArrayList<>(filter), true)));
        List<Object> placed = new ArrayList<>(List.of(c));
        placed.addAll(filter);
        out.add(Map.entry("Placed", new Query("select count(distinct s.id)" + base
                + " join college_offers o on o.student_id = s.id and o.college_id = ? and o.status in " + PLACED_OFFER + " where 1 = 1" + f, placed, true)));
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private void add(Def d) {
        defs.put(d.key(), d);
    }

    static int clamp(Integer v, int min, int max, int fallback) {
        return v == null ? fallback : Math.max(min, Math.min(max, v));
    }

    private static Map<Dimension, String> dims(Object... kv) {
        Map<Dimension, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Dimension) kv[i], (String) kv[i + 1]);
        return m;
    }

    private static Map<Dimension, String> with(Map<Dimension, String> base, Dimension d, String sql) {
        Map<Dimension, String> m = new LinkedHashMap<>(base);
        m.put(d, sql);
        return m;
    }

    private static Map<Dimension, String> without(Map<Dimension, String> base, Dimension d) {
        Map<Dimension, String> m = new LinkedHashMap<>(base);
        m.remove(d);
        return m;
    }
}
