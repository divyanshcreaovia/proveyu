package com.proveyu.admin.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
import com.proveyu.cxo.domain.CxoMember;
import com.proveyu.cxo.domain.CxoModel.CreateCxoRequest;
import com.proveyu.cxo.domain.CxoModel.CxoUserRow;
import com.proveyu.cxo.infrastructure.CxoMemberRepository;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.UserBlockList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CXO accounts (college leadership). There is no CXO sign-up: a platform admin creates the account for one approved college and
 * shares the password; the CXO can change it with "Forgot password". Revoking blocks the account at once.
 */
@Service
@RequiredArgsConstructor
public class AdminCxoService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final CxoMemberRepository members;
    private final CollegeProfileRepository colleges;
    private final PasswordEncoder passwordEncoder;
    private final UserBlockList blockList;
    private final AdminAuditService audit;

    @Transactional(readOnly = true)
    public List<CxoUserRow> list() {
        return jdbc.query("""
                select u.id, m.id, u.full_name, u.email, m.designation, m.college_id, c.college_name, m.status, u.is_active, m.last_login_at, m.created_at
                  from cxo_members m
                  join users u on u.id = m.user_id
                  left join college_profiles c on c.college_id = m.college_id
                 order by c.college_name, u.full_name""",
                (rs, i) -> new CxoUserRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4),
                        rs.getString(5), UUID.fromString(rs.getString(6)), rs.getString(7), rs.getString(8), rs.getBoolean(9),
                        rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(),
                        rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant()));
    }

    @Transactional
    public CxoUserRow create(UUID adminId, CreateCxoRequest r) {
        CollegeProfile college = colleges.findById(r.collegeId())
                .orElseThrow(() -> new DomainException("College not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (college.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new DomainException("Only an approved college can get leadership accounts", HttpStatus.BAD_REQUEST, "COLLEGE_NOT_APPROVED");
        }
        if (!r.password().matches(".*[A-Za-z].*") || !r.password().matches(".*\\d.*")) {
            throw new DomainException("The password needs at least 8 characters with a letter and a number", HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
        }
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new DomainException("This e-mail already has an account", HttpStatus.CONFLICT, "EMAIL_EXISTS");

        User u = users.save(User.builder().email(email).fullName(r.fullName().trim()).passwordHash(passwordEncoder.encode(r.password()))
                .role(Role.CXO).active(true).emailVerifiedAt(Instant.now()).build());
        String designation = r.designation() == null || r.designation().isBlank() ? null : r.designation().trim();
        CxoMember m = members.save(CxoMember.builder().collegeId(college.getCollegeId()).userId(u.getId()).designation(designation)
                .status(MemberStatus.ACTIVE).createdBy(adminId).build());
        audit.log(adminId, "CXO_CREATED", "USER", u.getId().toString(),
                "Created CXO account " + u.getFullName() + " (" + email + ") for " + college.getCollegeName());
        return new CxoUserRow(u.getId(), m.getId(), u.getFullName(), u.getEmail(), designation, college.getCollegeId(), college.getCollegeName(),
                MemberStatus.ACTIVE.name(), true, null, m.getCreatedAt() == null ? Instant.now() : m.getCreatedAt());
    }

    /** Revoke (blocks sign-in and ends open sessions) or restore a CXO account. */
    @Transactional
    public void setActive(UUID adminId, UUID memberId, boolean active) {
        CxoMember m = members.findById(memberId).orElseThrow(() -> new DomainException("CXO account not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        User u = users.findById(m.getUserId()).orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        m.setStatus(active ? MemberStatus.ACTIVE : MemberStatus.REVOKED);
        members.save(m);
        u.setActive(active);
        users.save(u);
        if (active) blockList.unblock(u.getId()); else blockList.block(u.getId());
        audit.log(adminId, active ? "CXO_RESTORED" : "CXO_REVOKED", "USER", u.getId().toString(),
                (active ? "Restored" : "Revoked") + " CXO account " + u.getFullName() + " (" + u.getEmail() + ")");
    }
}
