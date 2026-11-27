package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Offers: record, status lifecycle (OFFERED -> ACCEPTED/DECLINED/REVOKED, ACCEPTED -> JOINED/REVOKED),
 * offer letters, multi-offer tracking. Student PLACED status / highest CTC are derived after every change.
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private static final Map<OfferStatus, Set<OfferStatus>> TRANSITIONS = Map.of(
            OfferStatus.OFFERED, EnumSet.of(OfferStatus.ACCEPTED, OfferStatus.DECLINED, OfferStatus.REVOKED),
            OfferStatus.ACCEPTED, EnumSet.of(OfferStatus.JOINED, OfferStatus.REVOKED, OfferStatus.DECLINED),
            OfferStatus.JOINED, EnumSet.of(OfferStatus.REVOKED),
            OfferStatus.DECLINED, EnumSet.noneOf(OfferStatus.class),
            OfferStatus.REVOKED, EnumSet.noneOf(OfferStatus.class));

    private final CollegeOfferRepository offerRepository;
    private final com.proveyu.college.infrastructure.CollegeStudentRepository studentRows;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeDirectory directory;
    private final StudentService studentService;
    private final CollegeFileService fileService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;

    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> page(CollegeContext ctx, OfferStatus status, UUID companyId, UUID driveId, Integer batchYear, String q, int page, int size) {
        return PageResponse.of(all(ctx, status, companyId, driveId, batchYear, q), Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> all(CollegeContext ctx, OfferStatus status, UUID companyId, UUID driveId, Integer batchYear, String q) {
        var offers = offerRepository.findAllByCollegeId(ctx.collegeId());
        var m = maps(ctx.collegeId(), offers);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return offers.stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> companyId == null || companyId.equals(o.getCompanyId()))
                .filter(o -> driveId == null || driveId.equals(o.getDriveId()))
                .map(o -> toResponse(o, m))
                .filter(r -> r.studentId() != null && ctx.canAccessBranch(m.students().get(r.studentId()).getBranchId()))
                .filter(r -> batchYear == null || batchYear.equals(r.batchYear()))
                .filter(r -> needle.isEmpty() || r.studentName().toLowerCase(Locale.ROOT).contains(needle) || r.rollNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || (r.companyName() != null && r.companyName().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(OfferResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OfferResponse create(CollegeContext ctx, OfferRequest r) {
        studentRows.lockRow(r.studentId().toString(), ctx.collegeId().toString()); // double clicks wait here, then see the first offer
        requireDateOrder(r.offerDate() == null ? LocalDate.now() : r.offerDate(), r.joiningDate());
        CollegeStudent s = studentService.scoped(ctx, r.studentId());
        CollegeCompany company = directory.company(ctx.collegeId(), r.companyId());
        CollegeDrive drive = r.driveId() == null ? null : directory.drive(ctx.collegeId(), r.driveId());
        if (drive != null && !drive.getCompanyId().equals(company.getId())) {
            throw new DomainException("Drive belongs to a different company", HttpStatus.BAD_REQUEST, "DRIVE_MISMATCH");
        }
        if (s.getStatus() == StudentStatus.DEBARRED || s.getStatus() == StudentStatus.OPTED_OUT) {
            throw new DomainException("Student is " + s.getStatus(), HttpStatus.UNPROCESSABLE_ENTITY, "STUDENT_INELIGIBLE");
        }
        if (drive != null && offerRepository.findAllByCollegeIdAndStudentId(ctx.collegeId(), s.getId()).stream()
                .anyMatch(o -> drive.getId().equals(o.getDriveId()) && EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus()))) {
            throw new DomainException("Student already has an active offer from this drive", HttpStatus.CONFLICT, "DUPLICATE_OFFER");
        }
        List<CollegeTier> tiers = directory.tiers(ctx.collegeId());
        CollegeDrive policyProbe = drive != null ? drive : CollegeDrive.builder().ctcLpa(r.ctcLpa()).build();
        var violations = EligibilityEngine.policyViolations(policyProbe, directory.profile(ctx.collegeId()), tiers,
                offerRepository.findAllByCollegeIdAndStudentId(ctx.collegeId(), s.getId()));
        if (!violations.isEmpty()) {
            if (!r.overridePolicy() || r.overrideReason() == null || r.overrideReason().trim().length() < 5) {
                throw new DomainException("Placement policy: " + String.join("; ", violations) + ". Tick 'override policy' and give a reason to proceed.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_VIOLATION");
            }
            auditService.log(ctx, "POLICY_OVERRIDE", "STUDENT", s.getId(), "Offer recorded despite policy for " + s.getRollNumber(), r.overrideReason());
        }
        CollegeTier tier = EligibilityEngine.tierFor(r.ctcLpa(), tiers);
        Integer tierRank = drive != null && drive.getTierId() != null ? EligibilityEngine.driveTierRank(drive, tiers) : (tier == null ? null : tier.getTierRank());
        CollegeOffer o = offerRepository.save(CollegeOffer.builder().collegeId(ctx.collegeId()).studentId(s.getId()).driveId(r.driveId())
                .companyId(company.getId()).roleTitle(r.roleTitle()).ctcLpa(r.ctcLpa()).ctcBreakup(r.ctcBreakup()).tierRank(tierRank)
                .offerDate(r.offerDate() == null ? LocalDate.now() : r.offerDate()).joiningDate(r.joiningDate()).remarks(r.remarks()).build());
        if (drive != null) {
            applicationRepository.findByDriveIdAndStudentId(drive.getId(), s.getId()).ifPresent(a -> {
                a.setStatus(ApplicationStatus.SELECTED);
                applicationRepository.save(a);
            });
        }
        studentService.recomputePlacement(ctx.collegeId(), s);
        String ctc = r.ctcLpa().stripTrailingZeros().toPlainString() + " LPA";
        notificationService.notify(s.getUserId(), "OFFER", "Offer from " + company.getName() + " (" + r.roleTitle() + ", " + ctc + "). Accept or decline it in your portal.");
        mailService.send(ctx.collegeId(), "OFFER_RELEASED", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s),
                "companyName", company.getName(), "roleTitle", r.roleTitle(), "ctc", ctc));
        auditService.log(ctx, "OFFER_CREATED", "OFFER", o.getId(), "Offer " + company.getName() + " " + ctc + " for " + s.getRollNumber(), null);
        return one(ctx, o);
    }

    @Transactional
    public OfferResponse update(CollegeContext ctx, UUID id, OfferRequest r) {
        requireDateOrder(r.offerDate(), r.joiningDate());
        CollegeOffer o = directory.offer(ctx.collegeId(), id);
        studentService.scoped(ctx, o.getStudentId());
        o.setRoleTitle(r.roleTitle());
        o.setCtcLpa(r.ctcLpa());
        o.setCtcBreakup(r.ctcBreakup());
        o.setOfferDate(r.offerDate());
        o.setJoiningDate(r.joiningDate());
        o.setRemarks(r.remarks());
        CollegeTier tier = EligibilityEngine.tierFor(r.ctcLpa(), directory.tiers(ctx.collegeId()));
        if (o.getDriveId() == null) o.setTierRank(tier == null ? null : tier.getTierRank());
        offerRepository.save(o);
        studentService.recomputePlacement(ctx.collegeId(), directory.student(ctx.collegeId(), o.getStudentId()));
        auditService.log(ctx, "OFFER_UPDATED", "OFFER", id, "Updated offer details", null);
        return one(ctx, o);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OfferResponse changeStatus(CollegeContext ctx, UUID id, OfferStatusRequest r) {
        offerRepository.studentIdOf(id, ctx.collegeId()).ifPresent(sid -> studentRows.lockRow(sid.toString(), ctx.collegeId().toString()));
        CollegeOffer o = directory.offer(ctx.collegeId(), id);
        CollegeStudent s = studentService.scoped(ctx, o.getStudentId());
        return transition(ctx.collegeId(), ctx.userId(), ctx.actorName(), o, s, r.status(), r.remarks());
    }

    /** Shared by TPO and student self-service (student may only ACCEPT / DECLINE an OFFERED offer). */
    @Transactional
    public OfferResponse transition(UUID collegeId, UUID actorId, String actorName, CollegeOffer o, CollegeStudent s, OfferStatus to, String remarks) {
        OfferStatus from = o.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new DomainException("Cannot change an offer from " + from + " to " + to, HttpStatus.CONFLICT, "INVALID_TRANSITION");
        }
        if (to == OfferStatus.ACCEPTED) {
            var policy = directory.profile(collegeId);
            if (policy.isBlockAfterAccept() && policy.getPolicyMode() == PolicyMode.ONE_OFFER
                    && offerRepository.findAllByCollegeIdAndStudentId(collegeId, s.getId()).stream()
                    .anyMatch(x -> !x.getId().equals(o.getId()) && (x.getStatus() == OfferStatus.ACCEPTED || x.getStatus() == OfferStatus.JOINED))) {
                throw new DomainException("Another offer is already accepted (one-offer policy). Decline it first.", HttpStatus.CONFLICT, "ALREADY_ACCEPTED");
            }
        }
        o.setStatus(to);
        if (remarks != null) o.setRemarks(remarks);
        offerRepository.save(o);
        studentService.recomputePlacement(collegeId, s);
        auditService.log(collegeId, actorId, actorName, "OFFER_" + to, "OFFER", o.getId(), "Offer for " + s.getRollNumber() + ": " + from + " -> " + to, remarks);
        if (!actorId.equals(s.getUserId())) notificationService.notify(s.getUserId(), "OFFER", "Your offer status changed to " + to + ".");
        return one(collegeId, o);
    }

    @Transactional
    public OfferResponse uploadLetter(CollegeContext ctx, UUID id, MultipartFile file) {
        CollegeOffer o = directory.offer(ctx.collegeId(), id);
        studentService.scoped(ctx, o.getStudentId());
        CollegeFile f = fileService.store(ctx.collegeId(), ctx.userId(), file, "OFFER_LETTER", CollegeFileService.PDF_EXT);
        o.setOfferLetterFileId(f.getId());
        offerRepository.save(o);
        auditService.log(ctx, "OFFER_LETTER", "OFFER", id, "Uploaded offer letter " + f.getOriginalName(), null);
        return one(ctx, o);
    }

    @Transactional(readOnly = true)
    public CollegeFile letter(UUID collegeId, CollegeOffer o) {
        if (o.getOfferLetterFileId() == null) throw CollegeDirectory.notFound("Offer letter");
        return fileService.get(collegeId, o.getOfferLetterFileId());
    }

    private static void requireDateOrder(LocalDate offerDate, LocalDate joiningDate) {
        if (offerDate != null && joiningDate != null && joiningDate.isBefore(offerDate)) {
            throw new DomainException("Joining date cannot be before the offer date", HttpStatus.BAD_REQUEST, "BAD_JOINING_DATE");
        }
    }

    // ---------------------------------------------------------------- mapping

    record Maps(Map<UUID, CollegeStudent> students, Map<UUID, CollegeCompany> companies, Map<UUID, CollegeDrive> drives,
                Map<UUID, CollegeBranch> branches, List<CollegeTier> tiers) {}

    Maps maps(UUID collegeId, List<CollegeOffer> offers) {
        return new Maps(directory.students(collegeId, offers.stream().map(CollegeOffer::getStudentId).collect(Collectors.toSet())),
                directory.companies(collegeId, offers.stream().map(CollegeOffer::getCompanyId).collect(Collectors.toSet())),
                directory.drives(collegeId, offers.stream().map(CollegeOffer::getDriveId).filter(Objects::nonNull).collect(Collectors.toSet())),
                directory.branches(collegeId), directory.tiers(collegeId));
    }

    private OfferResponse one(CollegeContext ctx, CollegeOffer o) {
        return one(ctx.collegeId(), o);
    }

    public OfferResponse one(UUID collegeId, CollegeOffer o) {
        return toResponse(o, maps(collegeId, List.of(o)));
    }

    OfferResponse toResponse(CollegeOffer o, Maps m) {
        CollegeStudent s = m.students().get(o.getStudentId());
        CollegeCompany c = m.companies().get(o.getCompanyId());
        CollegeDrive d = o.getDriveId() == null ? null : m.drives().get(o.getDriveId());
        CollegeBranch b = s == null || s.getBranchId() == null ? null : m.branches().get(s.getBranchId());
        String tierName = o.getTierRank() == null ? null : m.tiers().stream().filter(t -> t.getTierRank().equals(o.getTierRank()))
                .map(CollegeTier::getName).findFirst().orElse(null);
        return new OfferResponse(o.getId(), s == null ? null : s.getId(), s == null ? null : s.getRollNumber(), s == null ? null : CollegeDirectory.fullName(s),
                b == null ? null : b.getCode(), s == null ? null : s.getBatchYear(), o.getDriveId(), d == null ? null : d.getTitle(), o.getCompanyId(),
                c == null ? null : c.getName(), o.getRoleTitle(), o.getCtcLpa(), o.getCtcBreakup(), o.getTierRank(), tierName, o.getOfferDate(),
                o.getJoiningDate(), o.getStatus(), o.getOfferLetterFileId(), o.getRemarks(), o.getCreatedAt());
    }
}
