package com.proveyu.admin.application;

import com.proveyu.admin.domain.AdminModel.AdminRow;
import com.proveyu.admin.domain.AdminModel.CreateAdminRequest;
import com.proveyu.admin.domain.AdminModel.UserPage;
import com.proveyu.admin.domain.AdminModel.UserRow;
import com.proveyu.auth.application.AuthService;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.ForgotPasswordRequest;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.UserBlockList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Every account on the platform: search, block / unblock, password reset, and the platform admins themselves. There is no admin
 * sign-up: a new admin is created only by a signed-in admin.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> ROLES = Set.of("CANDIDATE", "RECRUITER", "ADMIN", "COLLEGE_ADMIN", "COLLEGE_COORDINATOR", "COLLEGE_VIEWER", "ORGANIZATION", "CXO");

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final UserBlockList blockList;
    private final AdminAuditService audit;

    /** Users, newest first; q matches name, e-mail, phone or a student's roll number. */
    @Transactional(readOnly = true)
    public UserPage search(String q, String role, Boolean active, int page, int size) {
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            String r = role.trim().toUpperCase(Locale.ROOT);
            if (!ROLES.contains(r)) throw new DomainException("Unknown role", HttpStatus.BAD_REQUEST, "BAD_ROLE");
            where.append(" and u.role = ?"); args.add(r);
        }
        if (active != null) { where.append(" and u.is_active = ?"); args.add(active ? 1 : 0); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" and (lower(u.full_name) like ? or lower(u.email) like ? or u.phone like ? or lower(s.roll_number) like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        String from = """
                 from users u
                 left join college_students s on s.user_id = u.id and s.deleted_at is null
                 left join college_members m on m.user_id = u.id and m.status = 'ACTIVE'
                 left join cxo_members x on x.user_id = u.id
                 left join college_profiles c on c.college_id = coalesce(s.college_id, m.college_id, x.college_id)""";
        Long total = jdbc.queryForObject("select count(distinct u.id)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(sz); pageArgs.add(pg * sz);
        List<UserRow> rows = jdbc.query("""
                select u.id, u.full_name, u.email, u.phone, u.role, u.is_active, u.email_verified_at is not null, u.created_at,
                       min(c.college_name), min(coalesce(s.roll_number, m.role, x.designation))""" + from + where
                        + " group by u.id, u.full_name, u.email, u.phone, u.role, u.is_active, u.email_verified_at, u.created_at"
                        + " order by u.created_at desc limit ? offset ?",
                (rs, i) -> new UserRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBoolean(6), rs.getBoolean(7), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getString(9),
                        rs.getString(10)),
                pageArgs.toArray());
        return new UserPage(rows, total == null ? 0 : total, pg, sz);
    }

    /** Blocked users cannot sign in, and sessions already open stop working at once. */
    @Transactional
    public void setActive(UUID adminId, UUID userId, boolean active) {
        User u = users.findById(userId).orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!active && u.getId().equals(adminId)) throw new DomainException("You cannot block yourself", HttpStatus.BAD_REQUEST, "SELF_BLOCK");
        if (!active && u.getRole() == Role.ADMIN && activeAdmins() <= 1) {
            throw new DomainException("At least one platform admin must stay active", HttpStatus.BAD_REQUEST, "LAST_ADMIN");
        }
        if (u.isActive() == active) return;
        u.setActive(active);
        users.save(u);
        if (active) blockList.unblock(userId); else blockList.block(userId);
        audit.log(adminId, active ? "USER_UNBLOCKED" : "USER_BLOCKED", "USER", userId.toString(),
                (active ? "Unblocked " : "Blocked ") + u.getFullName() + " (" + u.getEmail() + ", " + u.getRole() + ")");
    }

    /** E-mails the user a 6-digit code to set a new password (the same flow as "Forgot password"). */
    @Transactional
    public String sendPasswordReset(UUID adminId, UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        var r = authService.forgotPassword(new ForgotPasswordRequest(u.getEmail()));
        audit.log(adminId, "PASSWORD_RESET_SENT", "USER", userId.toString(), "Sent a password reset code to " + u.getEmail());
        return r.getDevOtp(); // only filled in development
    }

    // ------------------------------------------------------------------ platform admins

    @Transactional(readOnly = true)
    public List<AdminRow> admins() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN)
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(u -> new AdminRow(u.getId(), u.getFullName(), u.getEmail(), u.isActive(), u.getCreatedAt())).toList();
    }

    @Transactional
    public AdminRow createAdmin(UUID adminId, CreateAdminRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (!r.password().matches(".*[A-Za-z].*") || !r.password().matches(".*\\d.*")) {
            throw new DomainException("The password needs at least 8 characters with a letter and a number", HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
        }
        if (users.existsByEmail(email)) throw new DomainException("This e-mail already has an account", HttpStatus.CONFLICT, "EMAIL_EXISTS");
        User u = users.save(User.builder().email(email).fullName(r.fullName().trim()).passwordHash(passwordEncoder.encode(r.password()))
                .role(Role.ADMIN).active(true).emailVerifiedAt(Instant.now()).build());
        audit.log(adminId, "ADMIN_CREATED", "USER", u.getId().toString(), "Created platform admin " + u.getFullName() + " (" + email + ")");
        return new AdminRow(u.getId(), u.getFullName(), u.getEmail(), u.isActive(), u.getCreatedAt());
    }

    private long activeAdmins() {
        Long n = jdbc.queryForObject("select count(*) from users where role = 'ADMIN' and is_active = 1", Long.class);
        return n == null ? 0 : n;
    }
}
