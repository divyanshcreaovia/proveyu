package com.proveyu.college.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.application.dto.TeamDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.college.security.CollegeContextResolver;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final CollegeMemberRepository memberRepository;
    private final CollegeInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final CollegeDirectory directory;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    public static final List<RoleMatrixRow> MATRIX = List.of(
            new RoleMatrixRow("View dashboard, students, drives, reports", true, true, true),
            new RoleMatrixRow("Add / edit / import students", true, true, false),
            new RoleMatrixRow("Create & manage drives, rounds, interviews", true, true, false),
            new RoleMatrixRow("Record offers & eligibility overrides", true, true, false),
            new RoleMatrixRow("Send announcements", true, true, false),
            new RoleMatrixRow("Export reports", true, true, true),
            new RoleMatrixRow("Invite / revoke team members, change roles", true, false, false),
            new RoleMatrixRow("Placement policy, tiers, branches, settings", true, false, false),
            new RoleMatrixRow("View audit log", true, false, false),
            new RoleMatrixRow("Branch-scoped access (coordinators only)", false, true, true));

    @Transactional(readOnly = true)
    public List<MemberResponse> members(CollegeContext ctx) {
        var members = memberRepository.findAllByCollegeIdOrderByCreatedAtAsc(ctx.collegeId());
        var users = userRepository.findAllByIdIn(members.stream().map(CollegeMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        var branches = directory.branches(ctx.collegeId());
        return members.stream().map(m -> {
            User u = users.get(m.getUserId());
            var scope = new ArrayList<>(CollegeContextResolver.parseScope(m.getBranchScope()));
            return new MemberResponse(m.getId(), m.getUserId(), u == null ? "-" : u.getFullName(), u == null ? "-" : u.getEmail(),
                    u == null ? null : u.getPhone(), m.getRole(), m.getDesignation(), scope,
                    scope.stream().map(id -> branches.containsKey(id) ? branches.get(id).getName() : "?").toList(), m.getStatus(), m.getLastLoginAt(), m.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> invites(CollegeContext ctx) {
        return inviteRepository.findAllByCollegeIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(ctx.collegeId()).stream()
                .map(i -> new InviteResponse(i.getId(), i.getEmail(), i.getFullName(), i.getRole(), i.getDesignation(),
                        new ArrayList<>(CollegeContextResolver.parseScope(i.getBranchScope())), i.getExpiresAt(), i.getCreatedAt(), null)).toList();
    }

    @Transactional
    public InviteResponse invite(CollegeContext ctx, InviteRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("A PROVEYU account already exists for " + email, HttpStatus.CONFLICT, "EMAIL_EXISTS");
        }
        if (inviteRepository.existsByCollegeIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(ctx.collegeId(), email)) {
            throw new DomainException("An invitation is already pending for " + email, HttpStatus.CONFLICT, "INVITE_PENDING");
        }
        String scope = scopeCsv(ctx.collegeId(), r.role(), r.branchIds());
        String raw = TokenUtils.randomUrlToken();
        CollegeInvite inv = inviteRepository.save(CollegeInvite.builder().collegeId(ctx.collegeId()).email(email).fullName(r.fullName().trim())
                .role(r.role()).designation(r.designation()).branchScope(scope).tokenHash(TokenUtils.sha256(raw))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)).invitedBy(ctx.userId()).build());
        sendInviteMail(ctx, inv, raw);
        auditService.log(ctx, "MEMBER_INVITED", "MEMBER", inv.getId(), "Invited " + email + " as " + r.role(), null);
        return new InviteResponse(inv.getId(), inv.getEmail(), inv.getFullName(), inv.getRole(), inv.getDesignation(),
                new ArrayList<>(CollegeContextResolver.parseScope(scope)), inv.getExpiresAt(), inv.getCreatedAt(), appProperties.isExposeTokens() ? raw : null);
    }

    @Transactional
    public InviteResponse resend(CollegeContext ctx, UUID inviteId) {
        CollegeInvite inv = inviteRepository.findByIdAndCollegeId(inviteId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Invitation"));
        if (inv.getAcceptedAt() != null || inv.getRevokedAt() != null) throw new DomainException("Invitation is no longer pending", HttpStatus.CONFLICT, "INVITE_CLOSED");
        String raw = TokenUtils.randomUrlToken();
        inv.setTokenHash(TokenUtils.sha256(raw));
        inv.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        inviteRepository.save(inv);
        sendInviteMail(ctx, inv, raw);
        return new InviteResponse(inv.getId(), inv.getEmail(), inv.getFullName(), inv.getRole(), inv.getDesignation(),
                new ArrayList<>(CollegeContextResolver.parseScope(inv.getBranchScope())), inv.getExpiresAt(), inv.getCreatedAt(), appProperties.isExposeTokens() ? raw : null);
    }

    @Transactional
    public void revokeInvite(CollegeContext ctx, UUID inviteId) {
        CollegeInvite inv = inviteRepository.findByIdAndCollegeId(inviteId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Invitation"));
        inv.setRevokedAt(Instant.now());
        inviteRepository.save(inv);
        auditService.log(ctx, "INVITE_REVOKED", "MEMBER", inv.getId(), "Revoked invitation for " + inv.getEmail(), null);
    }

    @Transactional
    public MemberResponse update(CollegeContext ctx, UUID memberId, MemberUpdateRequest r) {
        CollegeMember m = memberRepository.findByIdAndCollegeId(memberId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Member"));
        if (m.getUserId().equals(ctx.userId()) && r.role() != m.getRole()) {
            throw new DomainException("You cannot change your own role", HttpStatus.CONFLICT, "SELF_ROLE_CHANGE");
        }
        if (m.getRole() == MemberRole.COLLEGE_ADMIN && r.role() != MemberRole.COLLEGE_ADMIN) requireAnotherAdmin(ctx.collegeId());
        MemberRole old = m.getRole();
        m.setRole(r.role());
        m.setDesignation(r.designation());
        m.setBranchScope(scopeCsv(ctx.collegeId(), r.role(), r.branchIds()));
        memberRepository.save(m);
        userRepository.findById(m.getUserId()).ifPresent(u -> { u.setRole(Role.valueOf(r.role().name())); userRepository.save(u); });
        auditService.log(ctx, "MEMBER_ROLE_CHANGED", "MEMBER", m.getId(), "Role " + old + " -> " + r.role(), "branchScope=" + m.getBranchScope());
        return members(ctx).stream().filter(x -> x.id().equals(memberId)).findFirst().orElseThrow();
    }

    @Transactional
    public MemberResponse setStatus(CollegeContext ctx, UUID memberId, MemberStatus status) {
        CollegeMember m = memberRepository.findByIdAndCollegeId(memberId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Member"));
        if (m.getUserId().equals(ctx.userId())) throw new DomainException("You cannot revoke yourself", HttpStatus.CONFLICT, "SELF_REVOKE");
        if (status == MemberStatus.REVOKED && m.getRole() == MemberRole.COLLEGE_ADMIN) requireAnotherAdmin(ctx.collegeId());
        m.setStatus(status);
        memberRepository.save(m);
        auditService.log(ctx, status == MemberStatus.REVOKED ? "MEMBER_REVOKED" : "MEMBER_RESTORED", "MEMBER", m.getId(), "Member access " + status, null);
        return members(ctx).stream().filter(x -> x.id().equals(memberId)).findFirst().orElseThrow();
    }

    private void requireAnotherAdmin(UUID collegeId) {
        if (memberRepository.countByCollegeIdAndRoleAndStatus(collegeId, MemberRole.COLLEGE_ADMIN, MemberStatus.ACTIVE) <= 1) {
            throw new DomainException("At least one active college admin is required", HttpStatus.CONFLICT, "LAST_ADMIN");
        }
    }

    private String scopeCsv(UUID collegeId, MemberRole role, List<UUID> branchIds) {
        if (role == MemberRole.COLLEGE_ADMIN || branchIds == null || branchIds.isEmpty()) return null;
        var branches = directory.branches(collegeId);
        if (!branches.keySet().containsAll(branchIds)) throw new DomainException("Unknown branch in scope", HttpStatus.BAD_REQUEST, "BRANCH_NOT_FOUND");
        return branchIds.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
    }

    private void sendInviteMail(CollegeContext ctx, CollegeInvite inv, String raw) {
        CollegeProfile p = directory.profile(ctx.collegeId());
        String link = appProperties.getFrontendUrl() + "/college/accept-invite?token=" + raw;
        emailService.sendHtml(inv.getEmail(), "You're invited to " + p.getCollegeName() + " on PROVEYU",
                "<p>Hi " + HtmlUtils.htmlEscape(inv.getFullName()) + ",</p><p>" + HtmlUtils.htmlEscape(ctx.actorName()) + " invited you to join the <b>"
                        + HtmlUtils.htmlEscape(p.getCollegeName()) + "</b> placement team as <b>" + inv.getRole().name().replace("COLLEGE_", "").toLowerCase(Locale.ROOT)
                        + "</b>.</p><p><a href=\"" + link + "\">Accept invitation</a> (valid for 7 days)</p>");
    }
}
