package com.proveyu.chat.application;

import com.proveyu.college.security.CollegeContextResolver;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Who may message whom. A college is a wall: its staff talk to each other and to its students (a branch-limited coordinator only to
 * students of those branches), and a company person talks only to the staff of a college that linked them or gave them a drive.
 * A platform admin may talk to anyone. Students do not message each other or companies.
 */
@Component
@RequiredArgsConstructor
public class ChatAccess {

    private record Staff(String collegeId, String branchScope) {}

    private record Student(String collegeId, String branchId) {}

    private final JdbcTemplate jdbc;

    public void check(UUID sender, UUID receiver) {
        if (isAdmin(sender) || isAdmin(receiver) || staffMay(sender, receiver) || staffMay(receiver, sender)) return;
        throw new DomainException("You cannot send messages to this person", HttpStatus.FORBIDDEN, "CHAT_NOT_ALLOWED");
    }

    /** Every allowed conversation (apart from admins) has an active staff member of a college on one side. */
    private boolean staffMay(UUID staffUser, UUID other) {
        for (Staff staff : staff(staffUser)) {
            if (staff(other).stream().anyMatch(s -> s.collegeId().equals(staff.collegeId()))) return true;
            if (students(other).stream().anyMatch(s -> s.collegeId().equals(staff.collegeId()) && covers(staff, s))) return true;
            if (companyPersonOf(staff.collegeId(), other)) return true;
        }
        return false;
    }

    private static boolean covers(Staff staff, Student student) {
        var scope = CollegeContextResolver.parseScope(staff.branchScope());
        return scope.isEmpty() || (student.branchId() != null && scope.contains(UUID.fromString(student.branchId())));
    }

    private boolean isAdmin(UUID user) {
        return exists("select count(*) from users where id = ? and role = 'ADMIN'", user.toString());
    }

    private List<Staff> staff(UUID user) {
        return jdbc.query("select m.college_id, m.branch_scope from college_members m join college_profiles cp on cp.college_id = m.college_id "
                        + "where m.user_id = ? and m.status = 'ACTIVE' and cp.approval_status <> 'SUSPENDED'",
                (rs, n) -> new Staff(rs.getString(1), rs.getString(2)), user.toString());
    }

    private List<Student> students(UUID user) {
        return jdbc.query("select s.college_id, s.branch_id from college_students s join college_profiles cp on cp.college_id = s.college_id "
                        + "where s.user_id = ? and s.deleted_at is null and cp.approval_status <> 'SUSPENDED'",
                (rs, n) -> new Student(rs.getString(1), rs.getString(2)), user.toString());
    }

    private boolean companyPersonOf(String collegeId, UUID user) {
        return exists("select count(*) from college_companies where college_id = ? and linked_recruiter_id = ? and deleted_at is null",
                        collegeId, user.toString())
                || exists("select count(*) from college_drive_recruiters where college_id = ? and user_id = ? and status = 'ACTIVE'",
                        collegeId, user.toString());
    }

    private boolean exists(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n != null && n > 0;
    }
}
