package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.ApplicationStatus;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final CollegeStudentRepository studentRepository;
    private final CollegeOfferRepository offerRepository;
    private final EligibilityOverrideRepository overrideRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeDirectory directory;
    private final AuditService auditService;

    public record Evaluated(CollegeStudent student, EligibilityEngine.Result result) {}

    /** Evaluates every (non-deleted) student of the college against the drive. */
    @Transactional(readOnly = true)
    public List<Evaluated> evaluateAll(UUID collegeId, CollegeDrive drive) {
        CollegeProfile policy = directory.profile(collegeId);
        List<CollegeTier> tiers = directory.tiers(collegeId);
        Map<UUID, List<CollegeOffer>> offers = offerRepository.findAllByCollegeId(collegeId).stream().collect(Collectors.groupingBy(CollegeOffer::getStudentId));
        Map<UUID, EligibilityOverride> overrides = overrideRepository.findAllByCollegeIdAndDriveId(collegeId, drive.getId()).stream()
                .collect(Collectors.toMap(EligibilityOverride::getStudentId, Function.identity()));
        return studentRepository.findAllByCollegeIdAndDeletedAtIsNull(collegeId).stream()
                .map(s -> new Evaluated(s, EligibilityEngine.evaluate(drive, s, policy, tiers, offers.getOrDefault(s.getId(), List.of()), overrides.get(s.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public EligibilityEngine.Result evaluate(UUID collegeId, CollegeDrive drive, CollegeStudent student) {
        return EligibilityEngine.evaluate(drive, student, directory.profile(collegeId), directory.tiers(collegeId),
                offerRepository.findAllByCollegeIdAndStudentId(collegeId, student.getId()),
                overrideRepository.findByDriveIdAndStudentId(drive.getId(), student.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public EligibilityPreview preview(CollegeContext ctx, UUID driveId, Boolean eligibleFilter, String q, int page, int size) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), driveId);
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        Map<UUID, ApplicationStatus> appStatus = applicationRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), driveId).stream()
                .collect(Collectors.toMap(CollegeApplication::getStudentId, CollegeApplication::getStatus));
        List<Evaluated> all = evaluateAll(ctx.collegeId(), drive).stream()
                .filter(e -> ctx.canAccessBranch(e.student().getBranchId())).toList();
        long eligible = all.stream().filter(e -> e.result().eligible()).count();
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        List<EligibilityRow> rows = all.stream()
                .filter(e -> eligibleFilter == null || e.result().eligible() == eligibleFilter)
                .filter(e -> needle == null || needle.isEmpty() || e.student().getRollNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || CollegeDirectory.fullName(e.student()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((Evaluated e) -> !e.result().eligible()).thenComparing(e -> e.student().getRollNumber()))
                .map(e -> {
                    CollegeStudent s = e.student();
                    CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
                    return new EligibilityRow(s.getId(), s.getRollNumber(), CollegeDirectory.fullName(s), b == null ? null : b.getCode(),
                            s.getBatchYear(), s.getCgpa(), s.getActiveBacklogs(), s.getStatus(), e.result().eligible(), e.result().overridden(),
                            e.result().reasons(), appStatus.get(s.getId()));
                }).toList();
        int sz = Math.min(Math.max(size, 1), 500);
        int from = Math.min(Math.max(page, 0) * sz, rows.size());
        int to = Math.min(from + sz, rows.size());
        return new EligibilityPreview(all.size(), eligible, all.size() - eligible, appStatus.size(), page, sz, rows.size(),
                (int) Math.ceil(rows.size() / (double) sz), rows.subList(from, to));
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> overrides(CollegeContext ctx, UUID driveId) {
        directory.drive(ctx.collegeId(), driveId);
        var list = overrideRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), driveId);
        var students = directory.students(ctx.collegeId(), list.stream().map(EligibilityOverride::getStudentId).collect(Collectors.toSet()));
        return list.stream().map(o -> {
            CollegeStudent s = students.get(o.getStudentId());
            return new OverrideResponse(o.getId(), o.getStudentId(), s == null ? null : s.getRollNumber(), s == null ? null : CollegeDirectory.fullName(s),
                    o.getAction(), o.getReason(), o.getCreatedAt());
        }).toList();
    }

    @Transactional
    public OverrideResponse addOverride(CollegeContext ctx, UUID driveId, OverrideRequest r) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), driveId);
        CollegeStudent s = directory.student(ctx.collegeId(), r.studentId());
        if (!ctx.canAccessBranch(s.getBranchId())) throw CollegeDirectory.notFound("Student");
        EligibilityOverride o = overrideRepository.findByDriveIdAndStudentId(driveId, s.getId())
                .orElse(EligibilityOverride.builder().collegeId(ctx.collegeId()).driveId(driveId).studentId(s.getId()).build());
        o.setAction(r.action());
        o.setReason(r.reason().trim());
        o.setCreatedBy(ctx.userId());
        o = overrideRepository.save(o);
        auditService.log(ctx, "ELIGIBILITY_OVERRIDE", "DRIVE", driveId, r.action() + " " + s.getRollNumber() + " for drive " + drive.getTitle(), r.reason());
        return new OverrideResponse(o.getId(), s.getId(), s.getRollNumber(), CollegeDirectory.fullName(s), o.getAction(), o.getReason(), o.getCreatedAt());
    }

    @Transactional
    public void removeOverride(CollegeContext ctx, UUID overrideId) {
        EligibilityOverride o = overrideRepository.findByIdAndCollegeId(overrideId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Override"));
        overrideRepository.delete(o);
        auditService.log(ctx, "ELIGIBILITY_OVERRIDE_REMOVED", "DRIVE", o.getDriveId(), "Removed " + o.getAction() + " override", o.getReason());
    }

    public void requireEligible(UUID collegeId, CollegeDrive drive, CollegeStudent s) {
        var r = evaluate(collegeId, drive, s);
        if (!r.eligible()) {
            throw new DomainException("Not eligible: " + String.join("; ", r.reasons()), HttpStatus.UNPROCESSABLE_ENTITY, "NOT_ELIGIBLE");
        }
    }
}
