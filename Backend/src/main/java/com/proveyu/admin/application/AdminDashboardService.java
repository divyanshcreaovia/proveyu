package com.proveyu.admin.application;

import com.proveyu.admin.domain.AdminModel.CollegeDetail;
import com.proveyu.admin.domain.AdminModel.Dashboard;
import com.proveyu.admin.domain.AdminModel.Member;
import com.proveyu.college.application.CollegeAdminService;
import com.proveyu.college.application.CollegeAdminService.CollegeSummary;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.UserBlockList;
import com.proveyu.student.interview.application.AiUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-wide numbers for the admin dashboard and the college detail. Read-only counts straight from the tables, so the admin
 * module does not need methods in every other module.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final JdbcTemplate jdbc;
    private final CollegeAdminService colleges;
    private final AdminAuditService audit;
    private final UserBlockList blockList;

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApprovalStatus s : ApprovalStatus.values()) byStatus.put(s.name(), 0L);
        jdbc.query("select approval_status, count(*) from college_profiles group by approval_status",
                rs -> { byStatus.put(rs.getString(1), rs.getLong(2)); });
        Map<String, Long> byRole = new LinkedHashMap<>();
        jdbc.query("select role, count(*) from users group by role order by role", rs -> { byRole.put(rs.getString(1), rs.getLong(2)); });
        String month = LocalDate.now().withDayOfMonth(1).toString();
        List<CollegeSummary> pending = colleges.list(ApprovalStatus.PENDING);
        return new Dashboard(byStatus, byRole,
                count("select count(*) from users where is_active = 0"),
                count("select count(*) from college_students where deleted_at is null"),
                count("select count(*) from college_students where deleted_at is null and status = 'PLACED'"),
                count("select count(*) from college_drives where deleted_at is null"),
                count("select count(*) from college_drives where deleted_at is null and status in ('PUBLISHED','ONGOING')"),
                count("select count(*) from college_offers"),
                count("select count(*) from college_offers where status in ('ACCEPTED','JOINED')"),
                count("select count(*) from prep_attempts"),
                count("select count(distinct attempt_id) from prep_proctor_events"),
                count("select count(*) from prep_attempts where auto_submit_reason = 'TAB_SWITCH'"),
                count("select count(*) from ai_interview_sessions"),
                count("select count(*) from ai_interview_sessions where status = 'COMPLETED'"),
                count("select count(*) from interview_purchases where status = 'SUBMITTED'"),
                count("select coalesce(sum(amount_paise), 0) div 100 from interview_purchases where status = 'PAID'"),
                count("select coalesce(sum(amount_paise), 0) div 100 from interview_purchases where status = 'PAID' and coalesce(reviewed_at, created_at) >= ?", month),
                AiUsage.snapshot(), pending.subList(0, Math.min(5, pending.size())), audit.recent(8));
    }

    @Transactional(readOnly = true)
    public CollegeDetail college(UUID collegeId) {
        CollegeSummary c = colleges.list(null).stream().filter(x -> x.collegeId().equals(collegeId)).findFirst()
                .orElseThrow(() -> new DomainException("College not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        String id = collegeId.toString();
        List<Member> team = jdbc.query("""
                select u.id, u.full_name, u.email, m.role, m.status from college_members m join users u on u.id = m.user_id
                where m.college_id = ? order by field(m.role, 'COLLEGE_ADMIN', 'COLLEGE_COORDINATOR', 'COLLEGE_VIEWER'), u.full_name""",
                (rs, i) -> new Member(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        blockList.isBlocked(rs.getString(1))), id);
        return new CollegeDetail(c,
                count("select count(*) from college_students where college_id = ? and deleted_at is null", id),
                count("select count(*) from college_students where college_id = ? and deleted_at is null and status = 'PLACED'", id),
                count("select count(*) from college_drives where college_id = ? and deleted_at is null", id),
                count("select count(*) from college_offers where college_id = ?", id),
                count("select count(*) from prep_attempts a join college_students s on s.user_id = a.user_id and s.deleted_at is null where s.college_id = ?", id),
                count("select count(*) from ai_interview_sessions where college_id = ?", id),
                team);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
