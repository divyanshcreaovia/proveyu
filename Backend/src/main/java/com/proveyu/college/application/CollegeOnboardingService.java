package com.proveyu.college.application;

import com.proveyu.auth.application.AuthService;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.AuthResponse;
import com.proveyu.college.application.dto.TeamDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.college.security.CollegeContextResolver;
import com.proveyu.organization.domain.OrgType;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.PayloadDecryptionUtils;
import com.proveyu.shared.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CollegeOnboardingService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CollegeProfileRepository profileRepository;
    private final CollegeMemberRepository memberRepository;
    private final CollegeInviteRepository inviteRepository;
    private final CollegeTierRepository tierRepository;
    private final CollegeBatchRepository batchRepository;
    private final PasswordEncoder passwordEncoder;
    private final PayloadDecryptionUtils payloadDecryptionUtils;
    private final AuthService authService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final CollegeDirectory directory;

    @Transactional
    public RegisterResponse register(CollegeRegisterRequest r) {
        String email = AuthService.normalizeEmail(r.tpoEmail());
        String domain = r.emailDomain().trim().toLowerCase(Locale.ROOT);
        if (!email.endsWith("@" + domain) && !email.endsWith("." + domain)) {
            throw new DomainException("TPO e-mail must belong to the official domain @" + domain, HttpStatus.BAD_REQUEST, "EMAIL_DOMAIN_MISMATCH");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("An account with this e-mail already exists", HttpStatus.CONFLICT, "EMAIL_EXISTS");
        }
        if (profileRepository.existsByAicteCodeIgnoreCase(r.aicteCode().trim())) {
            throw new DomainException("This AICTE/UGC code is already registered", HttpStatus.CONFLICT, "COLLEGE_EXISTS");
        }
        String phone = r.tpoPhone().replaceAll("[\\s-]", "");
        if (userRepository.existsByPhone(phone)) {
            throw new DomainException("Phone number is already registered", HttpStatus.CONFLICT, "PHONE_EXISTS");
        }
        User user = userRepository.save(User.builder().email(email).phone(phone).fullName(r.tpoName().trim()).role(Role.COLLEGE_ADMIN).active(true)
                .passwordHash(passwordEncoder.encode(payloadDecryptionUtils.decryptPassword(r.password()))).build());
        Organization org = organizationRepository.save(Organization.builder().userId(user.getId()).legalName(r.collegeName().trim())
                .orgType(OrgType.COLLEGE).codeOrGstin(r.aicteCode().trim()).contactEmail(email).contactPhone(phone)
                .address(r.city() + ", " + r.state()).verified(false).build());
        ApprovalStatus status = appProperties.isCollegeApprovalRequired() ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED;
        int year = LocalDate.now().getYear();
        profileRepository.save(CollegeProfile.builder().collegeId(org.getId()).collegeName(r.collegeName().trim()).aicteCode(r.aicteCode().trim())
                .university(r.university()).city(r.city()).state(r.state()).emailDomain(domain).website(r.website())
                .tpoName(r.tpoName()).tpoEmail(email).tpoPhone(phone).approvalStatus(status)
                .currentAcademicYear(year + "-" + String.valueOf(year + 1).substring(2)).build());
        memberRepository.save(CollegeMember.builder().collegeId(org.getId()).userId(user.getId()).role(MemberRole.COLLEGE_ADMIN)
                .designation("Training & Placement Officer").build());
        // sensible defaults: Normal / Dream / Super Dream tiers and the next two graduating batches
        tierRepository.save(CollegeTier.builder().collegeId(org.getId()).name("Normal").tierRank(1).minCtcLpa(BigDecimal.ZERO).build());
        tierRepository.save(CollegeTier.builder().collegeId(org.getId()).name("Dream").tierRank(2).minCtcLpa(new BigDecimal("8")).build());
        tierRepository.save(CollegeTier.builder().collegeId(org.getId()).name("Super Dream").tierRank(3).minCtcLpa(new BigDecimal("15")).build());
        for (int y = year; y <= year + 1; y++) {
            batchRepository.save(CollegeBatch.builder().collegeId(org.getId()).gradYear(y).label("Batch " + y).build());
        }
        auditService.log(org.getId(), user.getId(), user.getFullName(), "COLLEGE_REGISTERED", "COLLEGE", org.getId(), "College registered: " + r.collegeName(), null);
        String token = authService.issueEmailVerification(user);
        return new RegisterResponse(org.getId(), email, status,
                "Registration received. Check " + email + " to verify your account." + (status == ApprovalStatus.PENDING
                        ? " Your college will be reviewed by the PROVEYU team." : ""),
                appProperties.isExposeTokens() ? token : null);
    }

    @Transactional(readOnly = true)
    public InviteInfo inviteInfo(String rawToken) {
        CollegeInvite inv = validInvite(rawToken);
        return new InviteInfo(directory.profile(inv.getCollegeId()).getCollegeName(), inv.getEmail(), inv.getFullName(), inv.getRole(), inv.getExpiresAt());
    }

    @Transactional
    public AuthResponse acceptInvite(AcceptInviteRequest r) {
        CollegeInvite inv = validInvite(r.token());
        String email = AuthService.normalizeEmail(inv.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("An account already exists for " + email + ". Ask your TPO to invite a different e-mail.", HttpStatus.CONFLICT, "EMAIL_EXISTS");
        }
        String phone = r.phone() == null || r.phone().isBlank() || userRepository.existsByPhone(r.phone()) ? null : r.phone();
        User user = userRepository.save(User.builder().email(email).phone(phone)
                .fullName(r.fullName() == null || r.fullName().isBlank() ? inv.getFullName() : r.fullName().trim())
                .role(Role.valueOf(inv.getRole().name())).active(true).emailVerifiedAt(Instant.now())
                .passwordHash(passwordEncoder.encode(payloadDecryptionUtils.decryptPassword(r.password()))).build());
        memberRepository.save(CollegeMember.builder().collegeId(inv.getCollegeId()).userId(user.getId()).role(inv.getRole())
                .designation(inv.getDesignation()).branchScope(inv.getBranchScope()).build());
        inv.setAcceptedAt(Instant.now());
        inviteRepository.save(inv);
        auditService.log(inv.getCollegeId(), user.getId(), user.getFullName(), "INVITE_ACCEPTED", "MEMBER", user.getId(),
                user.getFullName() + " joined as " + inv.getRole(), null);
        return authService.issueSession(user);
    }

    private CollegeInvite validInvite(String rawToken) {
        CollegeInvite inv = inviteRepository.findByTokenHash(TokenUtils.sha256(rawToken == null ? "" : rawToken))
                .orElseThrow(() -> new DomainException("Invalid invitation link", HttpStatus.NOT_FOUND, "INVITE_INVALID"));
        if (inv.getAcceptedAt() != null) throw new DomainException("This invitation has already been used", HttpStatus.CONFLICT, "INVITE_USED");
        if (inv.getRevokedAt() != null) throw new DomainException("This invitation was revoked", HttpStatus.GONE, "INVITE_REVOKED");
        if (inv.getExpiresAt().isBefore(Instant.now())) throw new DomainException("This invitation has expired", HttpStatus.GONE, "INVITE_EXPIRED");
        return inv;
    }

    @Transactional(readOnly = true)
    public MeResponse me(CollegeContext ctx) {
        User u = userRepository.findById(ctx.userId()).orElseThrow(() -> CollegeDirectory.notFound("User"));
        CollegeProfile p = directory.profile(ctx.collegeId());
        CollegeMember m = memberRepository.findByUserId(ctx.userId()).orElseThrow();
        return new MeResponse(u.getId(), u.getFullName(), u.getEmail(), ctx.role(), m.getDesignation(),
                new ArrayList<>(CollegeContextResolver.parseScope(m.getBranchScope())), p.getCollegeId(), p.getCollegeName(), p.getApprovalStatus(),
                p.getApprovalNote(), p.getLogoFileId() != null, p.getBrandColor());
    }
}
