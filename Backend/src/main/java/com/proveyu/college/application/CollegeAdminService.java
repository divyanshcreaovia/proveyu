package com.proveyu.college.application;

import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Platform ADMIN review of newly registered colleges. */
@Service
@RequiredArgsConstructor
public class CollegeAdminService {

    public record CollegeSummary(UUID collegeId, String collegeName, String aicteCode, String university, String city, String state,
                                 String emailDomain, String tpoName, String tpoEmail, String tpoPhone, ApprovalStatus approvalStatus,
                                 String approvalNote, Instant createdAt) {}

    private final CollegeProfileRepository profileRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CollegeSummary> list(ApprovalStatus status) {
        var rows = status == null ? profileRepository.findAllByOrderByCreatedAtDesc() : profileRepository.findAllByApprovalStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::toSummary).toList();
    }

    @Transactional
    public CollegeSummary decide(UUID adminId, UUID collegeId, ApprovalStatus status, String note) {
        if (status == ApprovalStatus.PENDING) throw new DomainException("Choose APPROVED or REJECTED", HttpStatus.BAD_REQUEST, "BAD_DECISION");
        CollegeProfile p = profileRepository.findById(collegeId).orElseThrow(() -> CollegeDirectory.notFound("College"));
        p.setApprovalStatus(status);
        p.setApprovalNote(note);
        profileRepository.save(p);
        organizationRepository.findById(collegeId).ifPresent(o -> { o.setVerified(status == ApprovalStatus.APPROVED); organizationRepository.save(o); });
        String admin = userRepository.findById(adminId).map(u -> u.getFullName()).orElse("PROVEYU admin");
        auditService.log(collegeId, adminId, admin, "COLLEGE_" + status, "COLLEGE", collegeId, "College " + status.name().toLowerCase(), note);
        if (p.getTpoEmail() != null) {
            emailService.sendHtml(p.getTpoEmail(), "Your college registration was " + status.name().toLowerCase(),
                    "<p>Hello " + HtmlUtils.htmlEscape(p.getTpoName()) + ",</p><p>Your registration for <b>" + HtmlUtils.htmlEscape(p.getCollegeName())
                            + "</b> has been <b>" + status.name().toLowerCase() + "</b>.</p>" + (note == null ? "" : "<p>Note: " + HtmlUtils.htmlEscape(note) + "</p>"));
        }
        return toSummary(p);
    }

    private CollegeSummary toSummary(CollegeProfile p) {
        return new CollegeSummary(p.getCollegeId(), p.getCollegeName(), p.getAicteCode(), p.getUniversity(), p.getCity(), p.getState(),
                p.getEmailDomain(), p.getTpoName(), p.getTpoEmail(), p.getTpoPhone(), p.getApprovalStatus(), p.getApprovalNote(), p.getCreatedAt());
    }
}
