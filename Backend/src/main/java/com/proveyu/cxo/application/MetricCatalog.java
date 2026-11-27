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

    private static final String CGPA_BAND_SQL = """
            case when s.cgpa is null then 'No CGPA' when s.cgpa < 6 then 'Below 6' when s.cgpa < 7 then '6-7' when s.cgpa < 8 then '7-8'
                 when s.cgpa < 9 then '8-9' else '9 and above' end""";

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

        Map<Dimension, String> studentDims = dims(BRANCH, "b.code", BATCH, "cast(s.batch_year as char)", GENDER, "s.gender", CGPA_BAND, CGPA_BAND_SQL);

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

        String multiSet = "(select student_id from college_offers where college_id = ? and status in " + ACTIVE_OFFER + " group by student_id having count(*) >= 2)";
        add(new Def("new_placements", "New placements", "Students whose first accepted offer was made in the period (use for \"placed this month\")", Unit.COUNT,
                "from " + student + " join (select student_id, min(offer_date) d from college_offers where college_id = ? and status in " + PLACED_OFFER
                        + " group by student_id) fp on fp.student_id = s.id where s.college_id = ? and s.deleted_at is null", 2, "count(*)",
                with(studentDims, MONTH, "date_format(fp.d, '%Y-%m')"), "fp.d", "s.batch_year", "b.code", Map.of()));
        add(new Def("multi_offer_students", "Students with several offers", "Students holding two or more offers (offered, accepted or joined)", Unit.COUNT,
                "from " + student + " join " + multiSet + " p on p.student_id = s.id where s.college_id = ? and s.deleted_at is null", 2,
                "count(*)", studentDims, null, "s.batch_year", "b.code", Map.of()));
        add(new Def("zero_application_students", "Students who never applied", "Eligible students who have not applied to any drive", Unit.COUNT,
                "from " + student + " left join (select distinct student_id from college_applications where college_id = ?) ap on ap.student_id = s.id"
                        + " where s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT') and ap.student_id is null", 2,
                "count(*)", studentDims, null, "s.batch_year", "b.code", Map.of()));
        add(new Def("student_documents", "Student documents", "Documents students uploaded to the document locker, by verification status", Unit.COUNT,
                "from college_student_documents sd join college_students s on s.id = sd.student_id and s.deleted_at is null"
                        + " left join college_branches b on b.id = s.branch_id where sd.college_id = ?", 1, "count(*)",
                dims(STATUS, "sd.verification_status", TYPE, "sd.doc_type", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)"),
                null, "s.batch_year", "b.code", Map.of()));

        add(new Def("unplaced_list", "Unplaced students list", "Names of eligible students without an accepted offer, with roll number, "
                + "branch and batch, highest CGPA first (value: CGPA). Use with TABLE", Unit.CGPA,
                "from " + student + " left join " + placedSet + " p on p.student_id = s.id"
                        + " where s.college_id = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT') and p.student_id is null", 2,
                "max(s.cgpa)", dims(STUDENT, "concat(trim(concat(coalesce(s.first_name, ''), ' ', coalesce(s.last_name, ''))), ' (', "
                        + "coalesce(s.roll_number, '-'), ') · ', coalesce(b.code, '-'), ' · ', coalesce(cast(s.batch_year as char), '-'))"),
                null, "s.batch_year", "b.code", Map.of()));

        Map<Dimension, String> offerDims = dims(MONTH, "date_format(o.offer_date, '%Y-%m')", COMPANY, "c.name", BRANCH, "b.code",
                BATCH, "cast(s.batch_year as char)", TIER, "t.name", STATUS, "o.status", GENDER, "s.gender", CGPA_BAND, CGPA_BAND_SQL);
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

        add(new Def("lowest_ctc", "Lowest CTC", "Lowest CTC offered, in LPA", Unit.LPA, offers + " and o.ctc_lpa is not null", 1,
                "min(o.ctc_lpa)", without(offerDims, STATUS), "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("median_ctc", "Median CTC", "Median (middle) CTC of offers, in LPA: half the offers are above it, half below", Unit.LPA,
                offers + " and o.ctc_lpa is not null", 1, "o.ctc_lpa", without(offerDims, STATUS), "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("companies_hired", "Companies that hired", "Different companies that made at least one offer", Unit.COUNT, offers, 1,
                "count(distinct o.company_id)", without(without(offerDims, COMPANY), STATUS), "o.offer_date", "s.batch_year", "b.code", Map.of()));
        add(new Def("avg_cgpa", "Average CGPA", "Average CGPA of students on the roll (out of 10)", Unit.SCORE,
                "from " + student + " where s.college_id = ? and s.deleted_at is null and s.cgpa is not null", 1, "round(avg(s.cgpa), 2)",
                with(studentDims, STATUS, "s.status"), null, "s.batch_year", "b.code", Map.of()));

        String drives = "from college_drives d left join college_companies c on c.id = d.company_id where d.college_id = ? and d.deleted_at is null";
        Map<Dimension, String> driveDims = dims(STATUS, "d.status", TYPE, "d.drive_type", MONTH, "date_format(d.drive_date, '%Y-%m')", COMPANY, "c.name");
        add(new Def("drives", "Placement drives", "Placement drives (not deleted)", Unit.COUNT, drives, 1, "count(*)",
                driveDims, "d.drive_date", null, null, Map.of()));
        add(new Def("avg_drive_ctc", "Average package on offer", "Average CTC advertised by drives, in LPA", Unit.LPA, drives + " and d.ctc_lpa is not null", 1,
                "round(avg(d.ctc_lpa), 2)", without(driveDims, STATUS), "d.drive_date", null, null, Map.of()));

        Map<Dimension, String> appDims = dims(STATUS, "a.status", MONTH, "date_format(a.created_at, '%Y-%m')", COMPANY, "c.name", BRANCH, "b.code",
                BATCH, "cast(s.batch_year as char)", GENDER, "s.gender");
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
                BATCH, "cast(s.batch_year as char)", GENDER, "s.gender");
        add(new Def("mock_test_attempts", "Mock test attempts", "Submitted mock test and college test attempts", Unit.COUNT, attempts, 1,
                "count(*)", testDims, "pa.submitted_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("mock_test_avg_score", "Average test score", "Average score of submitted attempts, in %", Unit.PERCENT,
                attempts + " and pa.score is not null and pa.max_score > 0", 1, "round(avg(100 * pa.score / pa.max_score), 1)",
                testDims, "pa.submitted_at", "s.batch_year", "b.code", Map.of()));

        Map<Dimension, String> aiDims = dims(MONTH, "date_format(x.started_at, '%Y-%m')", TYPE, "x.interview_type", DIFFICULTY, "x.difficulty",
                BRANCH, "b.code", BATCH, "cast(s.batch_year as char)", GENDER, "s.gender");
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

        String coder = """
                from code_ground_submissions cs
                join college_students s on s.user_id = cs.user_id and s.college_id = ? and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                where 1 = 1""";
        Map<Dimension, String> coderDims = dims(MONTH, "date_format(date_add(cs.created_at, interval 330 minute), '%Y-%m')", LANGUAGE, "cs.language",
                BRANCH, "b.code", BATCH, "cast(s.batch_year as char)", GENDER, "s.gender");
        add(new Def("code_submissions", "Code ground submissions", "Solutions students submitted on Code ground (the coding practice area)", Unit.COUNT,
                coder, 1, "count(*)", with(coderDims, STATUS, "cs.verdict"), "cs.created_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("code_accept_rate", "Code ground accepted rate", "Submissions on Code ground that passed every test, as a % of all submissions",
                Unit.PERCENT, coder, 1, "round(100 * sum(cs.verdict = 'ACCEPTED') / nullif(count(*), 0), 1)", coderDims, "cs.created_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("code_active_coders", "Students practising code", "Different students who submitted at least one solution on Code ground", Unit.COUNT,
                coder, 1, "count(distinct cs.user_id)", coderDims, "cs.created_at", "s.batch_year", "b.code", Map.of()));
        add(new Def("code_problems_solved", "Code problems solved", "Problems solved on Code ground (each student counts a problem once)", Unit.COUNT,
                """
                from code_ground_progress cp
                join college_students s on s.user_id = cp.user_id and s.college_id = ? and s.deleted_at is null
                left join college_branches b on b.id = s.branch_id
                where cp.item_type = 'PROBLEM' and cp.done_at is not null""", 1, "count(*)",
                dims(MONTH, "date_format(date_add(cp.done_at, interval 330 minute), '%Y-%m')", BRANCH, "b.code", BATCH, "cast(s.batch_year as char)", GENDER, "s.gender"),
                "cp.done_at", "s.batch_year", "b.code", Map.of()));

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
        return defs.values().stream().map(d -> new MetricInfo(d.key(), d.label(), d.description(), d.unit(), dimensions(d), drillable(d))).toList();
    }

    List<Def> all() {
        return new ArrayList<>(defs.values());
    }

    public Optional<Def> find(String key) {
        return Optional.ofNullable(key == null ? null : defs.get(key.trim().toLowerCase()));
    }

    /** Dimensions a metric supports; NONE (a single number) first when allowed. */
    public List<Dimension> dimensions(Def d) {
        List<Dimension> out = new ArrayList<>();
        if (d.from() != null && d.unit() != Unit.CGPA) out.add(NONE); // a list (CGPA per student) has no meaningful single number
        out.addAll(d.dims().keySet());
        boolean list = d.unit() == Unit.CGPA || isFunnel(d);
        if (d.batchCol() != null && !list && !d.dims().containsKey(BATCH)) out.add(BATCH); // any student-based number can be split by batch
        else if (d.dateCol() != null && !d.dims().containsKey(BATCH) && d.batchCol() == null) out.add(YEAR);
        if (false) out.add(YEAR); // calendar years side by side, for metrics tied to a date only
        return out;
    }

    public boolean isFunnel(Def d) {
        return "placement_funnel".equals(d.key());
    }

    // ------------------------------------------------------------------ query building

    /** SQL for one widget. The spec must already be validated against this catalog. */
    /** The WHERE additions and bind values of a widget's filters (batch, years, branch, dates), shared by the numbers and the drill-down. */
    private record Filters(StringBuilder where, List<Object> args) {}

    private Filters filters(Def d, WidgetSpec w, UUID collegeId) {
        Dimension dim = w.dimension() == null ? NONE : w.dimension();
        List<Object> args = new ArrayList<>(Collections.nCopies(d.collegeParams(), collegeId.toString()));
        StringBuilder where = new StringBuilder();
        List<Integer> years = w.batches() == null || w.batches().isEmpty() ? null : w.batches();
        if (d.batchCol() != null && dim == BATCH && years != null) {
            where.append(" and ").append(d.batchCol()).append(" in (").append(String.join(",", Collections.nCopies(years.size(), "?"))).append(")");
            args.addAll(years);
        } else if (w.batchYear() != null && d.batchCol() != null) { where.append(" and ").append(d.batchCol()).append(" = ?"); args.add(w.batchYear()); }
        if (d.dateCol() != null && dim == YEAR && years != null) {
            where.append(" and year(").append(d.dateCol()).append(") in (").append(String.join(",", Collections.nCopies(years.size(), "?"))).append(")");
            args.addAll(years);
        }
        if (w.branchCode() != null && d.branchCol() != null) { where.append(" and ").append(d.branchCol()).append(" = ?"); args.add(w.branchCode()); }
        LocalDate from = w.from(), to = w.to();
        if (dim == MONTH) { LocalDate[] win = monthWindow(w); from = win[0]; to = win[1]; }
        if (d.dateCol() != null && from != null) { where.append(" and ").append(d.dateCol()).append(" >= ?"); args.add(from); }
        if (d.dateCol() != null && to != null) { where.append(" and ").append(d.dateCol()).append(" < ?"); args.add(to.plusDays(1)); }
        return new Filters(where, args);
    }

    private String labelSql(Def d, Dimension dim) {
        String sql = dim == YEAR ? "cast(year(" + d.dateCol() + ") as char)"
                : dim == BATCH && !d.dims().containsKey(BATCH) ? "cast(" + d.batchCol() + " as char)" : d.dims().get(dim);
        return "coalesce(" + sql + ", 'Unknown')";
    }

    /** The median is not a SQL aggregate here: its rows (label, value) come back and the engine takes the middle. */
    public boolean isMedian(Def d) {
        return "median_ctc".equals(d.key());
    }

    public Query build(Def d, WidgetSpec w, UUID collegeId) {
        Dimension dim = w.dimension() == null ? NONE : w.dimension();
        Filters f = filters(d, w, collegeId);
        StringBuilder where = f.where();
        List<Object> args = f.args();
        if (dim == NONE) return new Query("select " + d.value() + " " + d.from() + where, args, true);

        String label = labelSql(d, dim);
        if (dim == MONTH || dim == YEAR) where.append(" and ").append(d.dateCol()).append(" is not null");
        if (isMedian(d)) { // every offer's value with its label; no limit (the engine groups and ranks)
            return new Query("select " + label + " as label, " + d.value() + " as v " + d.from() + where, args, false);
        }
        String order = dim == CGPA_BAND ? "min(coalesce(s.cgpa, -1)) asc" : dim == MONTH || dim == BATCH || dim == YEAR ? "1 asc" : d.sortBy().containsKey(dim) ? d.sortBy().get(dim) + " asc" : "v desc, 1 asc";
        int limit = dim == MONTH ? 36 : dim == STUDENT ? clamp(w.limit(), 3, 50, 50) : dim == BATCH || dim == YEAR ? 12 : clamp(w.limit(), 3, 20, 10);
        args.add(limit);
        return new Query("select " + label + " as label, " + d.value() + " as v " + d.from() + where + " group by 1 order by " + order + " limit ?",
                args, false);
    }

    /** Whether the students behind a number can be listed: it is counted over students (not drives, companies, interviews...). */
    public boolean drillable(Def d) {
        return d.from() != null && d.batchCol() != null && d.from().contains("college_students") && !isFunnel(d);
    }

    /**
     * The students behind one bar (or a whole KPI when the label is null): the same FROM and filters as the number, one row per
     * student. One more row than {@code max} is asked for, so the caller can tell the list was cut.
     */
    public Query drill(Def d, WidgetSpec w, String label, UUID collegeId, int max) {
        Dimension dim = w.dimension() == null ? NONE : w.dimension();
        Filters f = filters(d, w, collegeId);
        StringBuilder where = f.where();
        List<Object> args = f.args();
        if (dim != NONE && label != null) {
            where.append(" and ").append(labelSql(d, dim)).append(" = ?");
            args.add(label);
        }
        if (dim == MONTH || dim == YEAR) where.append(" and ").append(d.dateCol()).append(" is not null");
        args.add(max + 1);
        return new Query("select distinct s.id as sid, trim(concat(coalesce(s.first_name, ''), ' ', coalesce(s.last_name, ''))) as name, s.roll_number as roll,"
                + " b.code as branch, s.batch_year as batch, s.cgpa as cgpa, s.status as status,"
                + " exists(select 1 from college_offers po where po.student_id = s.id and po.status in " + PLACED_OFFER + ") as placed "
                + d.from() + where + " order by name asc limit ?", args, false);
    }

    /**
     * The months a trend covers, as [first day, last day or null for open]. Dates asked for win; else a batch's own two years
     * (the year before it graduates and its graduation year, up to today) so a past batch's trend is not an empty last-12-months
     * chart; else the last 12 months.
     */
    public static LocalDate[] monthWindow(WidgetSpec w) {
        LocalDate today = LocalDate.now();
        LocalDate from = w.from(), to = w.to();
        if (from == null && w.batchYear() != null) {
            from = LocalDate.of(w.batchYear() - 1, 1, 1);
            if (to == null) to = LocalDate.of(w.batchYear(), 12, 31);
        }
        if (from == null) from = today.withDayOfMonth(1).minusMonths(11);
        if (to != null && to.isAfter(today.plusMonths(1))) to = today.withDayOfMonth(today.lengthOfMonth()); // no empty months in the future
        if (to != null && to.isBefore(from)) to = from;
        return new LocalDate[]{from, to};
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
