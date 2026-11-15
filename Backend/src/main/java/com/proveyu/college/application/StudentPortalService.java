package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.OfferResponse;
import com.proveyu.college.application.dto.StudentDtos.DocumentResponse;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Self-service "Campus Drives" portal for students (CANDIDATE accounts linked to a college student record). */
@Service
@RequiredArgsConstructor
public class StudentPortalService {

    public record PortalProfile(UUID studentId, String collegeName, String rollNumber, String name, String email, String branch, Integer batchYear,
                                BigDecimal cgpa, StudentStatus status, BigDecimal highestCtcLpa) {}

    public record PortalDrive(UUID id, String title, String companyName, String roleTitle, DriveType driveType, BigDecimal ctcLpa,
                              BigDecimal stipendMonthly, String location, DriveMode mode, String venue, Instant registrationDeadline, LocalDate driveDate,
                              DriveStatus status, String jobDescription, List<String> skills, int bondMonths, String tierName, boolean eligible,
                              List<String> reasons, UUID applicationId, ApplicationStatus applicationStatus, boolean canApply) {}

    public record PortalApplication(UUID id, UUID driveId, String driveTitle, String companyName, ApplicationStatus status, int currentRound, Instant appliedAt) {}

    private final CollegeStudentRepository studentRepository;
    private final CollegeDriveRepository driveRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeDirectory directory;
    private final EligibilityService eligibilityService;
    private final ApplicationService applicationService;
    private final OfferService offerService;
    private final StudentService studentService;

    public CollegeStudent me(UUID userId) {
        return studentRepository.findFirstByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new DomainException("Your account is not linked to a college. Ask your placement cell to add you.", HttpStatus.NOT_FOUND, "NOT_A_COLLEGE_STUDENT"));
    }

    @Transactional(readOnly = true)
    public PortalProfile profile(UUID userId) {
        CollegeStudent s = me(userId);
        CollegeBranch b = s.getBranchId() == null ? null : directory.branches(s.getCollegeId()).get(s.getBranchId());
        return new PortalProfile(s.getId(), directory.profile(s.getCollegeId()).getCollegeName(), s.getRollNumber(), CollegeDirectory.fullName(s),
                s.getEmail(), b == null ? null : b.getName(), s.getBatchYear(), s.getCgpa(), s.getStatus(), s.getHighestCtcLpa());
    }

    @Transactional(readOnly = true)
    public List<PortalDrive> drives(UUID userId) {
        CollegeStudent s = me(userId);
        Map<UUID, CollegeApplication> apps = applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .collect(Collectors.toMap(CollegeApplication::getDriveId, a -> a));
        var drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(s.getCollegeId()).stream()
                .filter(d -> d.getStatus() == DriveStatus.PUBLISHED || d.getStatus() == DriveStatus.ONGOING || apps.containsKey(d.getId()))
                .filter(d -> d.getStatus() != DriveStatus.DRAFT).toList();
        var companies = directory.companies(s.getCollegeId(), drives.stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        var tiers = directory.tiers(s.getCollegeId());
        Instant now = Instant.now();
        return drives.stream().map(d -> {
            var r = eligibilityService.evaluate(s.getCollegeId(), d, s);
            CollegeApplication a = apps.get(d.getId());
            boolean open = d.getStatus() == DriveStatus.PUBLISHED && (d.getRegistrationDeadline() == null || d.getRegistrationDeadline().isAfter(now));
            boolean canApply = open && r.eligible() && (a == null || a.getStatus() == ApplicationStatus.WITHDRAWN);
            Integer rank = EligibilityEngine.driveTierRank(d, tiers);
            String tierName = rank == null ? null : tiers.stream().filter(t -> t.getTierRank().equals(rank)).map(CollegeTier::getName).findFirst().orElse(null);
            return new PortalDrive(d.getId(), d.getTitle(), Optional.ofNullable(companies.get(d.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                    d.getRoleTitle(), d.getDriveType(), d.getCtcLpa(), d.getStipendMonthly(), d.getLocation(), d.getMode(), d.getVenue(),
                    d.getRegistrationDeadline(), d.getDriveDate(), d.getStatus(), d.getJobDescription(), StudentService.splitSkills(d.getSkills()),
                    d.getBondMonths(), tierName, r.eligible(), r.reasons(), a == null ? null : a.getId(), a == null ? null : a.getStatus(), canApply);
        }).sorted(Comparator.comparing((PortalDrive p) -> !p.canApply()).thenComparing(PortalDrive::registrationDeadline, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public PortalApplication apply(UUID userId, UUID driveId) {
        CollegeStudent s = me(userId);
        return toApp(s.getCollegeId(), applicationService.apply(s, driveId));
    }

    @Transactional
    public PortalApplication withdraw(UUID userId, UUID applicationId) {
        CollegeStudent s = me(userId);
        return toApp(s.getCollegeId(), applicationService.withdraw(s, applicationId));
    }

    @Transactional(readOnly = true)
    public List<PortalApplication> applications(UUID userId) {
        CollegeStudent s = me(userId);
        return applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .sorted(Comparator.comparing(CollegeApplication::getCreatedAt).reversed()).map(a -> toApp(s.getCollegeId(), a)).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> offers(UUID userId) {
        CollegeStudent s = me(userId);
        return offerRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream().map(o -> offerService.one(s.getCollegeId(), o)).toList();
    }

    @Transactional
    public OfferResponse respond(UUID userId, UUID offerId, boolean accept) {
        CollegeStudent s = me(userId);
        CollegeOffer o = directory.offer(s.getCollegeId(), offerId);
        if (!o.getStudentId().equals(s.getId())) throw CollegeDirectory.notFound("Offer");
        if (o.getStatus() != OfferStatus.OFFERED) throw new DomainException("This offer can no longer be changed", HttpStatus.CONFLICT, "OFFER_CLOSED");
        return offerService.transition(s.getCollegeId(), userId, CollegeDirectory.fullName(s), o, s, accept ? OfferStatus.ACCEPTED : OfferStatus.DECLINED,
                accept ? "Accepted by student" : "Declined by student");
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(UUID userId) {
        CollegeStudent s = me(userId);
        return studentService.documents(s.getCollegeId(), s.getId());
    }

    @Transactional
    public DocumentResponse upload(UUID userId, DocType type, MultipartFile file) {
        CollegeStudent s = me(userId);
        return studentService.uploadDocument(s.getCollegeId(), userId, s.getId(), type, file);
    }

    private PortalApplication toApp(UUID collegeId, CollegeApplication a) {
        CollegeDrive d = directory.drive(collegeId, a.getDriveId());
        CollegeCompany c = directory.company(collegeId, d.getCompanyId());
        return new PortalApplication(a.getId(), d.getId(), d.getTitle(), c.getName(), a.getStatus(), a.getCurrentRound(), a.getCreatedAt());
    }
}
