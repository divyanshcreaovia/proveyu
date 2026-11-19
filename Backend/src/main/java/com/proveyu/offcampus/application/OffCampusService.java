package com.proveyu.offcampus.application;

import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.offcampus.application.OffCampusRules.Criteria;
import com.proveyu.offcampus.application.OffCampusRules.Result;
import com.proveyu.offcampus.application.OffCampusRules.Student;
import com.proveyu.offcampus.domain.OffCampusModel.Applicant;
import com.proveyu.offcampus.domain.OffCampusModel.AppStatus;
import com.proveyu.offcampus.domain.OffCampusModel.ApplyLink;
import com.proveyu.offcampus.domain.OffCampusModel.CollegeRow;
import com.proveyu.offcampus.domain.OffCampusModel.CollegeView;
import com.proveyu.offcampus.domain.OffCampusModel.Counts;
import com.proveyu.offcampus.domain.OffCampusModel.Job;
import com.proveyu.offcampus.domain.OffCampusModel.JobPage;
import com.proveyu.offcampus.domain.OffCampusModel.JobRequest;
import com.proveyu.offcampus.domain.OffCampusModel.JobStatus;
import com.proveyu.offcampus.domain.OffCampusModel.JobField;
import com.proveyu.offcampus.domain.OffCampusModel.JobType;
import com.proveyu.offcampus.domain.OffCampusModel.Noc;
import com.proveyu.offcampus.domain.OffCampusModel.NocStatus;
import com.proveyu.offcampus.domain.OffCampusModel.Source;
import com.proveyu.offcampus.domain.OffCampusModel.Policy;
import com.proveyu.offcampus.domain.OffCampusModel.StudentJob;
import com.proveyu.offcampus.domain.OffCampusModel.StudentJobs;
import com.proveyu.offcampus.domain.OffCampusModel.StudentUpdate;
import com.proveyu.offcampus.domain.OffCampusModel.WorkMode;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Off-campus jobs. The ProveYu team posts a job with eligibility filters and publishes it; eligible students get a notification, see it
 * in the student portal and apply on the company's site. ProveYu records the click and the student keeps the status up to date. The
 * student's placement cell sees those applications and offers, and decides by policy whether its students may use off-campus jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffCampusService {

    private static final String JOB_COLS = "j.id, j.company_name, j.role_title, j.job_type, j.work_mode, j.field, j.location, j.ctc_min_lpa, j.ctc_max_lpa, "
            + "j.stipend_per_month, j.description, j.skills, j.apply_url, j.apply_deadline, j.batch_years, j.degree_keywords, j.branch_keywords, "
            + "j.min_cgpa, j.max_backlogs, j.college_ids, j.status, j.published_at, j.created_at, j.updated_at, j.source, j.source_posted_at";
    private static final String COUNTS_JOIN = " " + """
            left join (select job_id, sum(saved) saved_n, sum(clicked_at is not null) clicked_n, sum(applied_at is not null) applied_n,
                               sum(status = 'INTERVIEWING') interviewing_n, sum(status = 'OFFER') offers_n
                        from off_campus_applications group by job_id) c on c.job_id = j.id""";
    private static final String STUDENT_SQL = """
            select s.id, s.college_id, s.first_name, s.last_name, s.roll_number, s.batch_year, s.degree, b.name branch, s.cgpa,
                   s.active_backlogs, s.status, p.off_campus_policy
            from college_students s join college_profiles p on p.college_id = s.college_id left join college_branches b on b.id = s.branch_id
            """;

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final AdminAuditService audit;

    /** A student row with what the rules need. */
    record Me(UUID id, String name, String rollNumber, UUID userId, Student facts) {}

    // ================================================================== admin

    public JobPage list(JobStatus status, Source source, JobField field, String q, int page, int size) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(0, page);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" and j.status = ?"); args.add(status.name()); }
        if (source != null) { where.append(" and j.source = ?"); args.add(source.name()); }
        if (field != null) { where.append(" and j.field = ?"); args.add(field.name()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            where.append(" and (lower(j.company_name) like ? or lower(j.role_title) like ? or lower(coalesce(j.location, '')) like ?)");
            args.add(like);
            args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("select count(*) from off_campus_jobs j" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(page * size);
        List<Job> rows = jdbc.query("select " + JOB_COLS + ", c.saved_n, c.clicked_n, c.applied_n, c.interviewing_n, c.offers_n from off_campus_jobs j"
                + COUNTS_JOIN + where + " order by field(j.status, 'PUBLISHED', 'DRAFT', 'CLOSED'), coalesce(j.published_at, j.created_at) desc limit ? offset ?",
                (rs, i) -> job(rs, true), pageArgs.toArray());
        return new JobPage(rows, total == null ? 0 : total, page, size);
    }

    public Job get(UUID id) {
        return jdbc.query("select " + JOB_COLS + ", c.saved_n, c.clicked_n, c.applied_n, c.interviewing_n, c.offers_n from off_campus_jobs j"
                        + COUNTS_JOIN + " where j.id = ?", (rs, i) -> job(rs, true), id.toString()).stream().findFirst()
                .orElseThrow(() -> new DomainException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public Job create(UUID adminId, JobRequest r) {
        validate(r);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into off_campus_jobs (id, company_name, role_title, job_type, work_mode, field, location, ctc_min_lpa, ctc_max_lpa, stipend_per_month,
                    description, skills, apply_url, apply_deadline, batch_years, degree_keywords, branch_keywords, min_cgpa, max_backlogs, college_ids,
                    status, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)""",
                concat(List.of(id.toString()), values(r), List.of(adminId.toString())).toArray());
        audit.log(adminId, "OFF_CAMPUS_JOB_CREATED", "OFF_CAMPUS_JOB", id.toString(), "Created off-campus job " + r.companyName().trim() + " · " + r.roleTitle().trim());
        return get(id);
    }

    @Transactional
    public Job update(UUID adminId, UUID id, JobRequest r) {
        get(id);
        validate(r);
        jdbc.update("""
                update off_campus_jobs set company_name = ?, role_title = ?, job_type = ?, work_mode = ?, field = ?, location = ?, ctc_min_lpa = ?, ctc_max_lpa = ?,
                    stipend_per_month = ?, description = ?, skills = ?, apply_url = ?, apply_deadline = ?, batch_years = ?, degree_keywords = ?,
                    branch_keywords = ?, min_cgpa = ?, max_backlogs = ?, college_ids = ?
                where id = ?""", concat(values(r), List.of(id.toString())).toArray());
        audit.log(adminId, "OFF_CAMPUS_JOB_UPDATED", "OFF_CAMPUS_JOB", id.toString(), "Edited off-campus job " + r.companyName().trim() + " · " + r.roleTitle().trim());
        return get(id);
    }

    @Transactional
    public Job duplicate(UUID adminId, UUID id) {
        Job j = get(id);
        UUID copy = UUID.randomUUID();
        jdbc.update("""
                insert into off_campus_jobs (id, company_name, role_title, job_type, work_mode, field, location, ctc_min_lpa, ctc_max_lpa, stipend_per_month,
                    description, skills, apply_url, apply_deadline, batch_years, degree_keywords, branch_keywords, min_cgpa, max_backlogs, college_ids,
                    status, created_by)
                select ?, company_name, concat(role_title, ' (copy)'), job_type, work_mode, field, location, ctc_min_lpa, ctc_max_lpa, stipend_per_month,
                    description, skills, apply_url, null, batch_years, degree_keywords, branch_keywords, min_cgpa, max_backlogs, college_ids, 'DRAFT', ?
                from off_campus_jobs where id = ?""", copy.toString(), adminId.toString(), id.toString());
        audit.log(adminId, "OFF_CAMPUS_JOB_CREATED", "OFF_CAMPUS_JOB", copy.toString(), "Copied off-campus job " + j.companyName() + " · " + j.roleTitle());
        return get(copy);
    }

    /** Publish, close or move back to draft. The first publish notifies every eligible student. */
    @Transactional
    public Job setStatus(UUID adminId, UUID id, JobStatus status) {
        return setStatus(adminId, id, status, true);
    }

    @Transactional
    public Job setStatus(UUID adminId, UUID id, JobStatus status, boolean notify) {
        Job j = get(id);
        String extra = "";
        if (status == JobStatus.PUBLISHED) {
            if (j.applyDeadline() != null && j.applyDeadline().isBefore(Instant.now())) {
                throw new DomainException("The apply-by date has passed. Change it before publishing.", HttpStatus.BAD_REQUEST, "DEADLINE_PASSED");
            }
            jdbc.update("update off_campus_jobs set status = 'PUBLISHED', published_at = coalesce(published_at, now()) where id = ?", id.toString());
            if (j.publishedAt() == null && notify) extra = ", notified " + notifyEligible(j) + " eligible students";
        } else {
            jdbc.update("update off_campus_jobs set status = ? where id = ?", status.name(), id.toString());
        }
        audit.log(adminId, "OFF_CAMPUS_JOB_" + status.name(), "OFF_CAMPUS_JOB", id.toString(),
                "Marked off-campus job " + j.companyName() + " · " + j.roleTitle() + " as " + status.name().toLowerCase() + extra);
        return get(id);
    }

    /** Publish or close several jobs at once (the imported jobs). No notifications: one per job would flood the bell. */
    @Transactional
    public int bulkStatus(UUID adminId, List<UUID> ids, JobStatus status) {
        int done = 0;
        for (UUID id : ids) {
            try {
                setStatus(adminId, id, status, false);
                done++;
            } catch (DomainException e) {
                log.info("[OFF CAMPUS] bulk {} skipped {}: {}", status, id, e.getMessage());
            }
        }
        return done;
    }

    public List<Applicant> applicants(UUID jobId) {
        get(jobId);
        return jdbc.query("""
                select a.id, s.first_name, s.last_name, s.roll_number, s.email, p.college_name, b.name branch, s.batch_year, a.status, a.saved,
                       a.offer_ctc_lpa, a.note, a.clicked_at, a.applied_at, a.updated_at
                from off_campus_applications a join college_students s on s.id = a.student_id
                     join college_profiles p on p.college_id = a.college_id left join college_branches b on b.id = s.branch_id
                where a.job_id = ? and a.status is not null
                order by field(a.status, 'OFFER', 'INTERVIEWING', 'APPLIED', 'REJECTED', 'CLICKED', 'NOT_INTERESTED'), a.updated_at desc limit 2000""",
                (rs, i) -> new Applicant(UUID.fromString(rs.getString("id")), name(rs), rs.getString("roll_number"), rs.getString("email"),
                        rs.getString("college_name"), rs.getString("branch"), (Integer) rs.getObject("batch_year"), appStatus(rs.getString("status")),
                        rs.getBoolean("saved"), rs.getBigDecimal("offer_ctc_lpa"), rs.getString("note"), instant(rs.getTimestamp("clicked_at")),
                        instant(rs.getTimestamp("applied_at")), instant(rs.getTimestamp("updated_at"))), jobId.toString());
    }

    /** One notification per eligible student, written in one batch so a publish to thousands of students stays quick. */
    int notifyEligible(Job j) {
        Criteria c = criteria(j);
        StringBuilder sql = new StringBuilder(STUDENT_SQL.replace("select s.id,", "select s.user_id, i.fields interests, s.id,")
                + " left join off_campus_interests i on i.student_id = s.id"
                + " where s.deleted_at is null and s.user_id is not null and p.approval_status = 'APPROVED' and p.off_campus_policy <> 'DISABLED'");
        List<Object> args = new ArrayList<>();
        if (!c.batchYears().isEmpty()) {
            sql.append(" and s.batch_year in (").append(String.join(",", Collections.nCopies(c.batchYears().size(), "?"))).append(")");
            args.addAll(c.batchYears());
        }
        if (!c.collegeIds().isEmpty()) {
            sql.append(" and s.college_id in (").append(String.join(",", Collections.nCopies(c.collegeIds().size(), "?"))).append(")");
            c.collegeIds().forEach(id -> args.add(id.toString()));
        }
        List<String> userIds = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            Student s = facts(rs);
            // a student who picked fields only hears about jobs in those fields
            List<JobField> wanted = parseFields(rs.getString("interests"));
            boolean interested = wanted.isEmpty() || j.field() == null || j.field() == JobField.OTHER || wanted.contains(j.field());
            if (interested && OffCampusRules.visibleTo(c, s) && OffCampusRules.check(c, s).canApply()) userIds.add(rs.getString("user_id"));
        }, args.toArray());
        String message = "New off-campus job for you: " + j.roleTitle() + " at " + j.companyName() + ". Open Off-campus jobs to apply.";
        jdbc.batchUpdate("insert into notifications (id, user_id, type, message, is_read) values (?, ?, 'OFF_CAMPUS', ?, 0)",
                userIds.stream().map(u -> new Object[]{UUID.randomUUID().toString(), u, message}).toList());
        log.info("[OFF CAMPUS] {} · {} published, {} students notified", j.companyName(), j.roleTitle(), userIds.size());
        return userIds.size();
    }

    // ================================================================== student

    public StudentJobs jobsFor(UUID userId) {
        Me me = me(userId);
        List<JobField> interests = interests(me.id());
        if (me.facts().policy() == Policy.DISABLED) return new StudentJobs(Policy.DISABLED, interests, List.of());
        Map<String, Noc> nocs = latestNocs(me.id());
        List<StudentJob> jobs = new ArrayList<>();
        jdbc.query("select " + JOB_COLS + ", a.status a_status, a.saved a_saved, a.offer_ctc_lpa a_ctc, a.note a_note from off_campus_jobs j"
                + " left join off_campus_applications a on a.job_id = j.id and a.student_id = ?"
                + " where (j.status = 'PUBLISHED' and (j.apply_deadline is null or j.apply_deadline > now())) or (a.id is not null and j.status <> 'DRAFT')"
                + " order by coalesce(j.published_at, j.created_at) desc limit 500", rs -> {
            Job j = job(rs, false);
            AppStatus mine = appStatus(rs.getString("a_status"));
            Criteria c = criteria(j);
            if (mine == null && !rs.getBoolean("a_saved") && !OffCampusRules.visibleTo(c, me.facts())) return;
            Result r = OffCampusRules.check(c, me.facts());
            boolean open = open(j);
            List<String> reasons = new ArrayList<>(r.reasons());
            if (!open) reasons.add(0, "Applications are closed");
            boolean canApply = open && r.canApply();
            jobs.add(new StudentJob(j.id(), j.companyName(), j.roleTitle(), j.jobType(), j.workMode(), j.field(), j.location(), j.ctcMinLpa(), j.ctcMaxLpa(),
                    j.stipendPerMonth(), j.description(), j.skills(), canApply ? j.applyUrl() : null, j.applyDeadline(), j.publishedAt(), r.eligible(),
                    reasons, canApply, mine, rs.getBoolean("a_saved"), rs.getBigDecimal("a_ctc"), rs.getString("a_note"), j.source(),
                    nocs.get(j.id().toString())));
        }, me.id().toString());
        return new StudentJobs(me.facts().policy(), interests, jobs);
    }

    public List<JobField> interests(UUID studentId) {
        return jdbc.queryForList("select fields from off_campus_interests where student_id = ?", String.class, studentId.toString()).stream()
                .findFirst().map(OffCampusService::parseFields).orElse(List.of());
    }

    @Transactional
    public List<JobField> setInterests(UUID userId, List<JobField> fields) {
        Me me = me(userId);
        List<JobField> clean = fields.stream().filter(Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            jdbc.update("delete from off_campus_interests where student_id = ?", me.id().toString());
        } else {
            jdbc.update("insert into off_campus_interests (student_id, fields) values (?, ?) on duplicate key update fields = values(fields)",
                    me.id().toString(), String.join(",", clean.stream().map(Enum::name).toList()));
        }
        return clean;
    }

    static List<JobField> parseFields(String csv) {
        return OffCampusRules.keywords(csv).stream().map(OffCampusService::field).filter(Objects::nonNull).distinct().toList();
    }

    /** Records that the student opened the apply link and returns it. */
    @Transactional
    public ApplyLink click(UUID userId, UUID jobId) {
        Me me = me(userId);
        Job j = jobForStudent(me, jobId);
        requireCanApply(me, j);
        jdbc.update("""
                insert into off_campus_applications (id, job_id, student_id, user_id, college_id, status, clicked_at) values (?, ?, ?, ?, ?, 'CLICKED', now())
                on duplicate key update clicked_at = coalesce(clicked_at, now()), status = coalesce(status, 'CLICKED')""",
                UUID.randomUUID().toString(), jobId.toString(), me.id().toString(), userId.toString(), me.facts().collegeId().toString());
        return new ApplyLink(j.applyUrl());
    }

    @Transactional
    public boolean toggleSave(UUID userId, UUID jobId) {
        Me me = me(userId);
        jobForStudent(me, jobId);
        jdbc.update("""
                insert into off_campus_applications (id, job_id, student_id, user_id, college_id, saved) values (?, ?, ?, ?, ?, 1)
                on duplicate key update saved = 1 - saved""",
                UUID.randomUUID().toString(), jobId.toString(), me.id().toString(), userId.toString(), me.facts().collegeId().toString());
        Boolean saved = jdbc.queryForObject("select saved from off_campus_applications where job_id = ? and student_id = ?", Boolean.class,
                jobId.toString(), me.id().toString());
        return Boolean.TRUE.equals(saved);
    }

    @Transactional
    public StudentJob updateStatus(UUID userId, UUID jobId, StudentUpdate r) {
        Me me = me(userId);
        Job j = jobForStudent(me, jobId);
        AppStatus from = jdbc.query("select status from off_campus_applications where job_id = ? and student_id = ?",
                (rs, i) -> appStatus(rs.getString(1)), jobId.toString(), me.id().toString()).stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (!OffCampusRules.canMove(from, r.status())) {
            throw new DomainException(from == null || from == AppStatus.CLICKED || from == AppStatus.NOT_INTERESTED
                    ? "Mark the job as applied first" : "That status can't be set", HttpStatus.BAD_REQUEST, "BAD_STATUS");
        }
        boolean starting = from == null || from == AppStatus.CLICKED || from == AppStatus.NOT_INTERESTED;
        if (starting && r.status() == AppStatus.APPLIED) requireCanApply(me, j);
        BigDecimal ctc = r.status() == AppStatus.OFFER ? r.offerCtcLpa() : null;
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        boolean applied = r.status() != AppStatus.NOT_INTERESTED;
        jdbc.update("""
                insert into off_campus_applications (id, job_id, student_id, user_id, college_id, status, offer_ctc_lpa, note, applied_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, if(?, now(), null))
                on duplicate key update status = values(status), offer_ctc_lpa = values(offer_ctc_lpa), note = values(note),
                    applied_at = if(?, coalesce(applied_at, now()), applied_at)""",
                UUID.randomUUID().toString(), jobId.toString(), me.id().toString(), userId.toString(), me.facts().collegeId().toString(),
                r.status().name(), ctc, note, applied, applied);
        if (r.status() == AppStatus.OFFER && from != AppStatus.OFFER) notifyCollegeOfOffer(me, j, ctc);
        return jobsFor(userId).jobs().stream().filter(x -> x.id().equals(jobId)).findFirst()
                .orElseThrow(() -> new DomainException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** The newest NOC request per job for this student, keyed by job id. */
    private Map<String, Noc> latestNocs(UUID studentId) {
        Map<String, Noc> out = new java.util.HashMap<>();
        jdbc.query("select * from off_campus_noc_requests where student_id = ? order by created_at desc", rs -> {
            out.putIfAbsent(rs.getString("job_id"), new Noc(UUID.fromString(rs.getString("id")), NocStatus.valueOf(rs.getString("status")),
                    rs.getString("student_note"), rs.getString("decision_note"), rs.getString("noc_file_id") != null, rs.getBoolean("noc_generated"),
                    rs.getString("decided_by_name"), instant(rs.getTimestamp("decided_at")), instant(rs.getTimestamp("created_at"))));
        }, studentId.toString());
        return out;
    }

    private void notifyCollegeOfOffer(Me me, Job j, BigDecimal ctc) {
        String msg = "Off-campus offer: " + me.name() + " (" + me.rollNumber() + ") got an offer from " + j.companyName() + " for " + j.roleTitle()
                + (ctc != null ? " at " + ctc.stripTrailingZeros().toPlainString() + " LPA" : "") + ". See Off-campus.";
        for (String admin : jdbc.queryForList("select user_id from college_members where college_id = ? and role = 'COLLEGE_ADMIN' and status = 'ACTIVE'",
                String.class, me.facts().collegeId().toString())) {
            notifications.notify(UUID.fromString(admin), "OFF_CAMPUS", msg);
        }
    }

    // ================================================================== college

    public CollegeView forCollege(CollegeContext ctx, AppStatus status, String q) {
        StringBuilder where = new StringBuilder(" where a.college_id = ? and a.status is not null");
        List<Object> args = new ArrayList<>(List.of(ctx.collegeId().toString()));
        if (!ctx.branchScope().isEmpty()) {
            where.append(" and s.branch_id in (").append(String.join(",", Collections.nCopies(ctx.branchScope().size(), "?"))).append(")");
            ctx.branchScope().forEach(b -> args.add(b.toString()));
        }
        Map<String, Object> sums = jdbc.queryForMap("""
                select count(distinct case when a.applied_at is not null then a.student_id end) students, sum(a.applied_at is not null) applied,
                       sum(a.status = 'INTERVIEWING') interviewing, sum(a.status = 'OFFER') offers
                from off_campus_applications a join college_students s on s.id = a.student_id""" + where, args.toArray());
        if (status != null) { where.append(" and a.status = ?"); args.add(status.name()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            where.append(" and (lower(j.company_name) like ? or lower(j.role_title) like ? or lower(concat(s.first_name, ' ', coalesce(s.last_name, ''))) like ?"
                    + " or lower(s.roll_number) like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        List<CollegeRow> rows = jdbc.query("""
                select a.id, a.job_id, j.company_name, j.role_title, j.job_type, a.student_id, s.first_name, s.last_name, s.roll_number, b.name branch,
                       s.batch_year, a.status, a.offer_ctc_lpa, a.note, a.clicked_at, a.applied_at, a.updated_at
                from off_campus_applications a join off_campus_jobs j on j.id = a.job_id join college_students s on s.id = a.student_id
                     left join college_branches b on b.id = s.branch_id""" + where
                        + " order by field(a.status, 'OFFER', 'INTERVIEWING', 'APPLIED', 'REJECTED', 'CLICKED', 'NOT_INTERESTED'), a.updated_at desc limit 2000",
                (rs, i) -> new CollegeRow(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("job_id")), rs.getString("company_name"),
                        rs.getString("role_title"), JobType.valueOf(rs.getString("job_type")), UUID.fromString(rs.getString("student_id")), name(rs),
                        rs.getString("roll_number"), rs.getString("branch"), (Integer) rs.getObject("batch_year"), appStatus(rs.getString("status")),
                        rs.getBigDecimal("offer_ctc_lpa"), rs.getString("note"), instant(rs.getTimestamp("clicked_at")), instant(rs.getTimestamp("applied_at")),
                        instant(rs.getTimestamp("updated_at"))), args.toArray());
        Policy policy = Policy.valueOf(jdbc.queryForObject("select off_campus_policy from college_profiles where college_id = ?", String.class,
                ctx.collegeId().toString()));
        Long pending = jdbc.queryForObject("select count(*) from off_campus_noc_requests where college_id = ? and status = 'PENDING'", Long.class,
                ctx.collegeId().toString());
        return new CollegeView(policy, num(sums.get("students")), num(sums.get("applied")), num(sums.get("interviewing")), num(sums.get("offers")),
                pending == null ? 0 : pending, rows);
    }

    // ================================================================== helpers

    Me me(UUID userId) {
        return jdbc.query(STUDENT_SQL + " where s.user_id = ? and s.deleted_at is null limit 1",
                        (rs, i) -> new Me(UUID.fromString(rs.getString("id")), name(rs), rs.getString("roll_number"), userId, facts(rs)), userId.toString())
                .stream().findFirst()
                .orElseThrow(() -> new DomainException("Your account isn't linked to a college", HttpStatus.FORBIDDEN, "NOT_A_STUDENT"));
    }

    /** The job, if this student may act on it: published (or already in the student's list) and offered to the student's college. */
    Job jobForStudent(Me me, UUID jobId) {
        if (me.facts().policy() == Policy.DISABLED) {
            throw new DomainException("Your placement cell has turned off off-campus jobs", HttpStatus.FORBIDDEN, "OFF_CAMPUS_DISABLED");
        }
        Job j = jdbc.query("select " + JOB_COLS + " from off_campus_jobs j where j.id = ? and j.status <> 'DRAFT'", (rs, i) -> job(rs, false), jobId.toString())
                .stream().findFirst().orElseThrow(() -> new DomainException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Integer mine = jdbc.queryForObject("select count(*) from off_campus_applications where job_id = ? and student_id = ?", Integer.class,
                jobId.toString(), me.id().toString());
        if ((mine == null || mine == 0) && !OffCampusRules.visibleTo(criteria(j), me.facts())) {
            throw new DomainException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return j;
    }

    private void requireCanApply(Me me, Job j) {
        if (!open(j)) throw new DomainException("Applications for this job are closed", HttpStatus.CONFLICT, "JOB_CLOSED");
        Result r = OffCampusRules.check(criteria(j), me.facts());
        if (!r.canApply()) throw new DomainException(String.join("; ", r.reasons()), HttpStatus.FORBIDDEN, "NOT_ELIGIBLE");
    }

    private static boolean open(Job j) {
        return j.status() == JobStatus.PUBLISHED && (j.applyDeadline() == null || j.applyDeadline().isAfter(Instant.now()));
    }

    private static void validate(JobRequest r) {
        if (r.ctcMinLpa() != null && r.ctcMaxLpa() != null && r.ctcMinLpa().compareTo(r.ctcMaxLpa()) > 0) {
            throw new DomainException("Minimum CTC is more than the maximum", HttpStatus.BAD_REQUEST, "BAD_CTC");
        }
    }

    /** Column values for insert / update, in JOB_COLS order (company_name .. college_ids). */
    private static List<Object> values(JobRequest r) {
        List<Object> v = new ArrayList<>();
        v.add(r.companyName().trim());
        v.add(r.roleTitle().trim());
        v.add(r.jobType().name());
        v.add(r.workMode().name());
        v.add((r.field() != null ? r.field() : JobField.classify(r.roleTitle())).name());
        v.add(blank(r.location()));
        v.add(r.ctcMinLpa());
        v.add(r.ctcMaxLpa());
        v.add(r.stipendPerMonth());
        v.add(blank(r.description()));
        v.add(r.skills() == null ? null : blank(String.join(",", r.skills().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList())));
        v.add(r.applyUrl().trim());
        v.add(r.applyDeadline() == null ? null : Timestamp.from(r.applyDeadline()));
        v.add(r.batchYears() == null ? null : blank(String.join(",", r.batchYears().stream().distinct().sorted().map(String::valueOf).toList())));
        v.add(blank(String.join(", ", OffCampusRules.keywords(r.degreeKeywords()))));
        v.add(blank(String.join(", ", OffCampusRules.keywords(r.branchKeywords()))));
        v.add(r.minCgpa());
        v.add(r.maxBacklogs());
        v.add(r.collegeIds() == null ? null : blank(String.join(",", r.collegeIds().stream().distinct().map(UUID::toString).toList())));
        return v;
    }

    private static Criteria criteria(Job j) {
        return new Criteria(j.batchYears(), OffCampusRules.keywords(j.degreeKeywords()), OffCampusRules.keywords(j.branchKeywords()), j.minCgpa(),
                j.maxBacklogs(), new HashSet<>(j.collegeIds()));
    }

    private static Student facts(ResultSet rs) throws SQLException {
        return new Student(UUID.fromString(rs.getString("college_id")), (Integer) rs.getObject("batch_year"), rs.getString("degree"), rs.getString("branch"),
                rs.getBigDecimal("cgpa"), rs.getInt("active_backlogs"), rs.getString("status"), Policy.valueOf(rs.getString("off_campus_policy")));
    }

    private static Job job(ResultSet rs, boolean withCounts) throws SQLException {
        Counts counts = withCounts ? new Counts(rs.getLong("saved_n"), rs.getLong("clicked_n"), rs.getLong("applied_n"), rs.getLong("interviewing_n"),
                rs.getLong("offers_n")) : null;
        return new Job(UUID.fromString(rs.getString("id")), rs.getString("company_name"), rs.getString("role_title"), JobType.valueOf(rs.getString("job_type")),
                WorkMode.valueOf(rs.getString("work_mode")), field(rs.getString("field")), rs.getString("location"), rs.getBigDecimal("ctc_min_lpa"), rs.getBigDecimal("ctc_max_lpa"),
                (Integer) rs.getObject("stipend_per_month"), rs.getString("description"), OffCampusRules.keywords(rs.getString("skills")),
                rs.getString("apply_url"), instant(rs.getTimestamp("apply_deadline")),
                OffCampusRules.keywords(rs.getString("batch_years")).stream().map(Integer::valueOf).toList(), rs.getString("degree_keywords"),
                rs.getString("branch_keywords"), rs.getBigDecimal("min_cgpa"), (Integer) rs.getObject("max_backlogs"),
                OffCampusRules.keywords(rs.getString("college_ids")).stream().map(UUID::fromString).toList(), JobStatus.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("published_at")), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
                Source.valueOf(rs.getString("source")), instant(rs.getTimestamp("source_posted_at")), counts);
    }

    static String name(ResultSet rs) throws SQLException {
        String last = rs.getString("last_name");
        return rs.getString("first_name") + (last == null || last.isBlank() ? "" : " " + last);
    }

    static JobField field(String s) {
        if (s == null) return null;
        try { return JobField.valueOf(s); } catch (IllegalArgumentException e) { return JobField.OTHER; }
    }

    private static AppStatus appStatus(String s) { return s == null ? null : AppStatus.valueOf(s); }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }

    @SafeVarargs
    private static List<Object> concat(List<?>... parts) {
        List<Object> out = new ArrayList<>();
        Arrays.stream(parts).forEach(out::addAll);
        return out;
    }
}
