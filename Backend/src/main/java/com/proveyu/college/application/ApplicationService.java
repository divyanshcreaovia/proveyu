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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** Statuses a TPO may set manually through bulk actions. Round progress drives IN_PROCESS / SELECTED automatically. */
    private static final Set<ApplicationStatus> MANUAL = EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.WAITLISTED,
            ApplicationStatus.REJECTED, ApplicationStatus.APPLIED, ApplicationStatus.SELECTED);
    private static final Set<ApplicationStatus> COUNTS_AS_ACTIVE = EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.WAITLISTED, ApplicationStatus.IN_PROCESS, ApplicationStatus.SELECTED);

    private final CollegeApplicationRepository applicationRepository;
    private final EligibilityOverrideRepository overrideRepository;
    private final CollegeDirectory directory;
    private final EligibilityService eligibilityService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(CollegeContext ctx, UUID driveId, ApplicationStatus status, String q, int page, int size) {
        directory.drive(ctx.collegeId(), driveId);
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), driveId);
        var students = directory.students(ctx.collegeId(), apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        var branches = directory.branches(ctx.collegeId());
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ApplicationResponse> rows = apps.stream()
                .filter(a -> status == null || a.getStatus() == status)
                .map(a -> toResponse(a, students.get(a.getStudentId()), branches))
                .filter(r -> r != null && ctx.canAccessBranch(students.get(r.studentId()).getBranchId()))
                .filter(r -> needle.isEmpty() || r.rollNumber().toLowerCase(Locale.ROOT).contains(needle) || r.studentName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(ApplicationResponse::rollNumber)).toList();
        return PageResponse.of(rows, Math.max(page, 0), Math.min(Math.max(size, 1), 500));
    }

    /** TPO registers students for a drive. Ineligible students need an override reason (audited INCLUDE override). */
    @Transactional
    public BulkResult addApplicants(CollegeContext ctx, UUID driveId, AddApplicantsRequest r) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), driveId);
        requireOpenForApplicants(drive);
        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (UUID sid : new LinkedHashSet<>(r.studentIds())) {
            CollegeStudent s = directory.students(ctx.collegeId(), List.of(sid)).get(sid);
            if (s == null || s.getDeletedAt() != null || !ctx.canAccessBranch(s.getBranchId())) { skipped.add(sid + ": not found"); continue; }
            if (applicationRepository.findByDriveIdAndStudentId(driveId, sid).isPresent()) { skipped.add(s.getRollNumber() + ": already registered"); continue; }
            var result = eligibilityService.evaluate(ctx.collegeId(), drive, s);
            ApplicationSource source = ApplicationSource.TPO;
            if (!result.eligible()) {
                if (r.overrideReason() == null || r.overrideReason().trim().length() < 5) {
                    skipped.add(s.getRollNumber() + ": not eligible (" + String.join("; ", result.reasons()) + ")");
                    continue;
                }
                eligibilityService.addOverride(ctx, driveId, new OverrideRequest(sid, OverrideAction.INCLUDE, r.overrideReason()));
                source = ApplicationSource.OVERRIDE;
            }
            applicationRepository.save(CollegeApplication.builder().collegeId(ctx.collegeId()).driveId(driveId).studentId(sid).source(source).build());
            notificationService.notify(s.getUserId(), "CAMPUS_DRIVE", "You have been registered for the " + drive.getTitle() + " drive.");
            added++;
        }
        auditService.log(ctx, "APPLICANTS_ADDED", "DRIVE", driveId, "Registered " + added + " student(s) for " + drive.getTitle(), String.join("\n", skipped));
        return new BulkResult(added, skipped);
    }

    @Transactional
    public BulkResult bulkStatus(CollegeContext ctx, BulkStatusRequest r) {
        if (!MANUAL.contains(r.status())) {
            throw new DomainException("Status " + r.status() + " is set automatically by round results", HttpStatus.BAD_REQUEST, "STATUS_NOT_MANUAL");
        }
        var apps = applicationRepository.findAllByIdInAndCollegeId(r.applicationIds(), ctx.collegeId());
        if (apps.isEmpty()) throw CollegeDirectory.notFound("Applications");
        var students = directory.students(ctx.collegeId(), apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        var drives = directory.drives(ctx.collegeId(), apps.stream().map(CollegeApplication::getDriveId).collect(Collectors.toSet()));
        int updated = 0;
        List<String> skipped = new ArrayList<>();
        for (CollegeApplication a : apps) {
            CollegeStudent s = students.get(a.getStudentId());
            if (s == null || !ctx.canAccessBranch(s.getBranchId())) { skipped.add(a.getId() + ": out of scope"); continue; }
            if (a.getStatus() == ApplicationStatus.WITHDRAWN) { skipped.add(s.getRollNumber() + ": withdrawn by student"); continue; }
            a.setStatus(r.status());
            if (r.remarks() != null) a.setRemarks(r.remarks());
            applicationRepository.save(a);
            CollegeDrive d = drives.get(a.getDriveId());
            notificationService.notify(s.getUserId(), "APPLICATION_STATUS", "Your application for " + (d == null ? "a drive" : d.getTitle()) + " is now " + r.status() + ".");
            if (r.status() == ApplicationStatus.SHORTLISTED && d != null) {
                CollegeCompany c = directory.company(ctx.collegeId(), d.getCompanyId());
                mailService.send(ctx.collegeId(), "SHORTLISTED", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s),
                        "companyName", c.getName(), "roundName", "the selection process"));
            }
            updated++;
        }
        auditService.log(ctx, "APPLICATIONS_" + r.status(), "APPLICATION", null, updated + " application(s) set to " + r.status(), r.remarks());
        return new BulkResult(updated, skipped);
    }

    // ---------------------------------------------------------------- student self-service

    @Transactional
    public CollegeApplication apply(CollegeStudent s, UUID driveId) {
        CollegeDrive drive = directory.drive(s.getCollegeId(), driveId);
        if (drive.getDeletedAt() != null || drive.getStatus() != DriveStatus.PUBLISHED
                || directory.company(s.getCollegeId(), drive.getCompanyId()).getDeletedAt() != null) {
            throw new DomainException("This drive is not open for registration", HttpStatus.CONFLICT, "DRIVE_CLOSED");
        }
        if (drive.getRegistrationDeadline() != null && drive.getRegistrationDeadline().isBefore(Instant.now())) {
            throw new DomainException("Registration deadline has passed", HttpStatus.CONFLICT, "DEADLINE_PASSED");
        }
        Optional<CollegeApplication> existing = applicationRepository.findByDriveIdAndStudentId(driveId, s.getId());
        if (existing.isPresent() && existing.get().getStatus() != ApplicationStatus.WITHDRAWN) {
            throw new DomainException("You have already applied", HttpStatus.CONFLICT, "ALREADY_APPLIED");
        }
        eligibilityService.requireEligible(s.getCollegeId(), drive, s);
        CollegeProfile policy = directory.profile(s.getCollegeId());
        if (policy.getMaxApplications() != null) {
            long active = applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                    .filter(a -> COUNTS_AS_ACTIVE.contains(a.getStatus())).count();
            if (active >= policy.getMaxApplications()) {
                throw new DomainException("Placement policy allows at most " + policy.getMaxApplications() + " active applications",
                        HttpStatus.UNPROCESSABLE_ENTITY, "MAX_APPLICATIONS");
            }
        }
        CollegeApplication a = existing.orElse(CollegeApplication.builder().collegeId(s.getCollegeId()).driveId(driveId).studentId(s.getId()).build());
        a.setStatus(ApplicationStatus.APPLIED);
        a.setSource(ApplicationSource.SELF);
        a.setCurrentRound(0);
        a = applicationRepository.save(a);
        auditService.log(s.getCollegeId(), s.getUserId(), CollegeDirectory.fullName(s), "STUDENT_APPLIED", "APPLICATION", a.getId(),
                s.getRollNumber() + " applied to " + drive.getTitle(), null);
        return a;
    }

    @Transactional
    public CollegeApplication withdraw(CollegeStudent s, UUID applicationId) {
        CollegeApplication a = applicationRepository.findByIdAndCollegeId(applicationId, s.getCollegeId())
                .filter(x -> x.getStudentId().equals(s.getId())).orElseThrow(() -> CollegeDirectory.notFound("Application"));
        if (a.getStatus() == ApplicationStatus.SELECTED || a.getStatus() == ApplicationStatus.IN_PROCESS) {
            throw new DomainException("You cannot withdraw once the selection process has started. Contact your placement cell.", HttpStatus.CONFLICT, "CANNOT_WITHDRAW");
        }
        if (a.getStatus() == ApplicationStatus.WITHDRAWN) throw new DomainException("You have already withdrawn this application", HttpStatus.CONFLICT, "ALREADY_WITHDRAWN");
        a.setStatus(ApplicationStatus.WITHDRAWN);
        auditService.log(s.getCollegeId(), s.getUserId(), CollegeDirectory.fullName(s), "STUDENT_WITHDREW", "APPLICATION", a.getId(), s.getRollNumber() + " withdrew", null);
        return applicationRepository.save(a);
    }

    // ---------------------------------------------------------------- helpers

    private void requireOpenForApplicants(CollegeDrive d) {
        if (d.getDeletedAt() != null || d.getStatus() == DriveStatus.COMPLETED || d.getStatus() == DriveStatus.CANCELLED) {
            throw new DomainException("Drive is closed", HttpStatus.CONFLICT, "DRIVE_CLOSED");
        }
    }

    public ApplicationResponse toResponse(CollegeApplication a, CollegeStudent s, Map<UUID, CollegeBranch> branches) {
        if (s == null) return null;
        CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
        return new ApplicationResponse(a.getId(), a.getDriveId(), s.getId(), s.getRollNumber(), CollegeDirectory.fullName(s), s.getEmail(),
                b == null ? null : b.getCode(), s.getBatchYear(), s.getCgpa(), a.getStatus(), a.getSource(), a.getCurrentRound(), a.getRemarks(), a.getCreatedAt());
    }
}
