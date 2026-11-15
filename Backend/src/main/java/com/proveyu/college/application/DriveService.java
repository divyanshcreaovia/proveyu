package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriveService {

    private static final Set<String> SORTABLE = Set.of("title", "driveDate", "ctcLpa", "createdAt", "status", "registrationDeadline");
    private static final Map<DriveStatus, Set<DriveStatus>> TRANSITIONS = Map.of(
            DriveStatus.DRAFT, EnumSet.of(DriveStatus.PUBLISHED, DriveStatus.CANCELLED),
            DriveStatus.PUBLISHED, EnumSet.of(DriveStatus.ONGOING, DriveStatus.CANCELLED, DriveStatus.DRAFT),
            DriveStatus.ONGOING, EnumSet.of(DriveStatus.COMPLETED, DriveStatus.CANCELLED),
            DriveStatus.COMPLETED, EnumSet.noneOf(DriveStatus.class),
            DriveStatus.CANCELLED, EnumSet.noneOf(DriveStatus.class));
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final CollegeDriveRepository driveRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final DriveRoundRepository roundRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeDirectory directory;
    private final EligibilityService eligibilityService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;

    public Specification<CollegeDrive> spec(UUID collegeId, String q, DriveStatus status, DriveType type, UUID companyId,
                                            LocalDate from, LocalDate to, boolean deleted) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("collegeId"), collegeId));
            p.add(deleted ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("roleTitle")), like)));
            }
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (type != null) p.add(cb.equal(root.get("driveType"), type));
            if (companyId != null) p.add(cb.equal(root.get("companyId"), companyId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("driveDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("driveDate"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<DriveResponse> page(CollegeContext ctx, String q, DriveStatus status, DriveType type, UUID companyId,
                                            LocalDate from, LocalDate to, boolean deleted, int page, int size, String sort, String dir) {
        Sort s = Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC, SORTABLE.contains(sort) ? sort : "createdAt");
        var result = driveRepository.findAll(spec(ctx.collegeId(), q, status, type, companyId, from, to, deleted),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), s));
        Ctx c = context(ctx.collegeId(), result.getContent());
        return PageResponse.of(result, d -> toResponse(d, c));
    }

    @Transactional(readOnly = true)
    public List<DriveResponse> all(CollegeContext ctx, String q, DriveStatus status, DriveType type, UUID companyId, LocalDate from, LocalDate to) {
        var list = driveRepository.findAll(spec(ctx.collegeId(), q, status, type, companyId, from, to, false), Sort.by(Sort.Direction.DESC, "createdAt"));
        Ctx c = context(ctx.collegeId(), list);
        return list.stream().map(d -> toResponse(d, c)).toList();
    }

    @Transactional(readOnly = true)
    public DriveResponse get(CollegeContext ctx, UUID id) {
        CollegeDrive d = directory.drive(ctx.collegeId(), id);
        return toResponse(d, context(ctx.collegeId(), List.of(d)));
    }

    @Transactional
    public DriveResponse create(CollegeContext ctx, DriveRequest r) {
        CollegeDrive d = CollegeDrive.builder().collegeId(ctx.collegeId()).createdBy(ctx.userId()).status(DriveStatus.DRAFT).build();
        apply(ctx.collegeId(), d, r);
        d = driveRepository.save(d);
        auditService.log(ctx, "DRIVE_CREATED", "DRIVE", d.getId(), "Created drive " + d.getTitle(), null);
        return get(ctx, d.getId());
    }

    @Transactional
    public DriveResponse update(CollegeContext ctx, UUID id, DriveRequest r) {
        CollegeDrive d = directory.drive(ctx.collegeId(), id);
        if (d.getStatus() == DriveStatus.COMPLETED || d.getStatus() == DriveStatus.CANCELLED) {
            throw new DomainException("Completed or cancelled drives cannot be edited", HttpStatus.CONFLICT, "DRIVE_LOCKED");
        }
        apply(ctx.collegeId(), d, r);
        driveRepository.save(d);
        auditService.log(ctx, "DRIVE_UPDATED", "DRIVE", id, "Updated drive " + d.getTitle(), null);
        return get(ctx, id);
    }

    @Transactional
    public DriveResponse cloneDrive(CollegeContext ctx, UUID id) {
        CollegeDrive src = directory.drive(ctx.collegeId(), id);
        CollegeDrive copy = CollegeDrive.builder().collegeId(ctx.collegeId()).companyId(src.getCompanyId()).title(src.getTitle() + " (Copy)")
                .roleTitle(src.getRoleTitle()).driveType(src.getDriveType()).ctcLpa(src.getCtcLpa()).stipendMonthly(src.getStipendMonthly())
                .ctcBreakup(src.getCtcBreakup()).location(src.getLocation()).bondMonths(src.getBondMonths()).bondDetails(src.getBondDetails())
                .jobDescription(src.getJobDescription()).skills(src.getSkills()).mode(src.getMode()).venue(src.getVenue()).tierId(src.getTierId())
                .minCgpa(src.getMinCgpa()).minTenth(src.getMinTenth()).minTwelfth(src.getMinTwelfth()).maxActiveBacklogs(src.getMaxActiveBacklogs())
                .maxGapYears(src.getMaxGapYears()).batchYears(src.getBatchYears()).branchIds(src.getBranchIds()).requiredSkills(src.getRequiredSkills())
                .status(DriveStatus.DRAFT).createdBy(ctx.userId()).build();
        copy = driveRepository.save(copy);
        for (DriveRound r : roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), id)) {
            roundRepository.save(DriveRound.builder().collegeId(ctx.collegeId()).driveId(copy.getId()).seq(r.getSeq()).name(r.getName())
                    .roundType(r.getRoundType()).build());
        }
        auditService.log(ctx, "DRIVE_CLONED", "DRIVE", copy.getId(), "Cloned drive " + src.getTitle(), null);
        return get(ctx, copy.getId());
    }

    @Transactional
    public DriveResponse transition(CollegeContext ctx, UUID id, TransitionRequest r) {
        CollegeDrive d = directory.drive(ctx.collegeId(), id);
        if (d.getDeletedAt() != null) throw new DomainException("Restore the drive first", HttpStatus.CONFLICT, "DRIVE_DELETED");
        DriveStatus from = d.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(r.status())) {
            throw new DomainException("Cannot move a drive from " + from + " to " + r.status(), HttpStatus.CONFLICT, "INVALID_TRANSITION");
        }
        if (r.status() == DriveStatus.PUBLISHED) {
            if (d.getCtcLpa() == null && d.getStipendMonthly() == null) {
                throw new DomainException("Set a CTC or stipend before publishing", HttpStatus.UNPROCESSABLE_ENTITY, "DRIVE_INCOMPLETE");
            }
            if (d.getRegistrationDeadline() != null && d.getRegistrationDeadline().isBefore(Instant.now())) {
                throw new DomainException("Registration deadline is in the past", HttpStatus.UNPROCESSABLE_ENTITY, "DRIVE_DEADLINE_PAST");
            }
            d.setPublishedAt(Instant.now());
        }
        d.setStatus(r.status());
        driveRepository.save(d);
        auditService.log(ctx, "DRIVE_" + r.status(), "DRIVE", id, "Drive " + d.getTitle() + ": " + from + " -> " + r.status(), r.reason());
        if (r.status() == DriveStatus.PUBLISHED && from == DriveStatus.DRAFT) notifyEligible(ctx.collegeId(), d);
        if (r.status() == DriveStatus.CANCELLED) notifyApplicants(ctx.collegeId(), d, "The " + d.getTitle() + " drive has been cancelled."
                + (r.reason() == null ? "" : " Reason: " + r.reason()));
        return get(ctx, id);
    }

    @Transactional
    public void softDelete(CollegeContext ctx, UUID id) {
        CollegeDrive d = directory.drive(ctx.collegeId(), id);
        if (d.getStatus() == DriveStatus.ONGOING) throw new DomainException("Complete or cancel an ongoing drive before deleting it", HttpStatus.CONFLICT, "DRIVE_ONGOING");
        d.setDeletedAt(Instant.now());
        driveRepository.save(d);
        auditService.log(ctx, "DRIVE_DELETED", "DRIVE", id, "Moved drive " + d.getTitle() + " to trash", null);
    }

    @Transactional
    public DriveResponse restore(CollegeContext ctx, UUID id) {
        CollegeDrive d = directory.drive(ctx.collegeId(), id);
        d.setDeletedAt(null);
        driveRepository.save(d);
        auditService.log(ctx, "DRIVE_RESTORED", "DRIVE", id, "Restored drive " + d.getTitle(), null);
        return get(ctx, id);
    }

    void notifyEligible(UUID collegeId, CollegeDrive d) {
        CollegeCompany company = directory.company(collegeId, d.getCompanyId());
        String deadline = d.getRegistrationDeadline() == null ? "the drive date" : WHEN.format(d.getRegistrationDeadline());
        String ctc = d.getCtcLpa() != null ? d.getCtcLpa().stripTrailingZeros().toPlainString() + " LPA"
                : (d.getStipendMonthly() == null ? "-" : "Stipend " + d.getStipendMonthly().stripTrailingZeros().toPlainString() + "/month");
        for (var e : eligibilityService.evaluateAll(collegeId, d)) {
            if (!e.result().eligible()) continue;
            CollegeStudent s = e.student();
            notificationService.notify(s.getUserId(), "CAMPUS_DRIVE", "New drive: " + company.getName() + " - " + d.getRoleTitle() + " (" + ctc + "). Apply before " + deadline + ".");
            mailService.send(collegeId, "DRIVE_PUBLISHED", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s), "companyName", company.getName(),
                    "roleTitle", d.getRoleTitle(), "ctc", ctc, "deadline", deadline));
        }
    }

    void notifyApplicants(UUID collegeId, CollegeDrive d, String message) {
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(collegeId, d.getId());
        directory.students(collegeId, apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()))
                .values().forEach(s -> notificationService.notify(s.getUserId(), "CAMPUS_DRIVE", message));
    }

    // ---------------------------------------------------------------- mapping

    private record Ctx(Map<UUID, CollegeCompany> companies, List<CollegeTier> tiers, Map<UUID, Map<String, Long>> appCounts,
                       Map<UUID, Long> rounds, Map<UUID, Long> offers) {}

    private Ctx context(UUID collegeId, List<CollegeDrive> drives) {
        Set<UUID> ids = drives.stream().map(CollegeDrive::getId).collect(Collectors.toSet());
        Map<UUID, Map<String, Long>> counts = new HashMap<>();
        Map<UUID, Long> rounds = new HashMap<>(), offers = new HashMap<>();
        if (!ids.isEmpty()) {
            applicationRepository.findAllByCollegeId(collegeId).stream().filter(a -> ids.contains(a.getDriveId()))
                    .forEach(a -> counts.computeIfAbsent(a.getDriveId(), k -> new TreeMap<>()).merge(a.getStatus().name(), 1L, Long::sum));
            for (UUID id : ids) {
                rounds.put(id, (long) roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(collegeId, id).size());
            }
            offerRepository.findAllByCollegeId(collegeId).stream().filter(o -> o.getDriveId() != null && ids.contains(o.getDriveId()))
                    .filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus()))
                    .forEach(o -> offers.merge(o.getDriveId(), 1L, Long::sum));
        }
        return new Ctx(directory.companies(collegeId, drives.stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet())),
                directory.tiers(collegeId), counts, rounds, offers);
    }

    private DriveResponse toResponse(CollegeDrive d, Ctx c) {
        CollegeCompany company = c.companies().get(d.getCompanyId());
        Integer rank = EligibilityEngine.driveTierRank(d, c.tiers());
        String tierName = rank == null ? null : c.tiers().stream().filter(t -> t.getTierRank().equals(rank)).map(CollegeTier::getName).findFirst().orElse(null);
        Eligibility e = new Eligibility(d.getMinCgpa(), d.getMinTenth(), d.getMinTwelfth(), d.getMaxActiveBacklogs(), d.getMaxGapYears(),
                EligibilityEngine.csv(d.getBatchYears()).stream().map(Integer::valueOf).sorted().toList(),
                EligibilityEngine.csv(d.getBranchIds()).stream().map(UUID::fromString).toList(),
                StudentService.splitSkills(d.getRequiredSkills()));
        return new DriveResponse(d.getId(), d.getCompanyId(), company == null ? null : company.getName(), d.getTitle(), d.getRoleTitle(),
                d.getDriveType(), d.getCtcLpa(), d.getStipendMonthly(), d.getCtcBreakup(), d.getLocation(), d.getBondMonths(), d.getBondDetails(),
                d.getJobDescription(), StudentService.splitSkills(d.getSkills()), d.getMode(), d.getVenue(), d.getRegistrationDeadline(),
                d.getDriveDate(), d.getTierId(), tierName, rank, d.getStatus(), e, d.getPublishedAt(), d.getDeletedAt() != null, d.getCreatedAt(),
                c.appCounts().getOrDefault(d.getId(), Map.of()), c.rounds().getOrDefault(d.getId(), 0L).intValue(),
                c.offers().getOrDefault(d.getId(), 0L).intValue());
    }

    private void apply(UUID collegeId, CollegeDrive d, DriveRequest r) {
        directory.company(collegeId, r.companyId());
        if (r.tierId() != null && directory.tiers(collegeId).stream().noneMatch(t -> t.getId().equals(r.tierId()))) {
            throw new DomainException("Unknown tier", HttpStatus.BAD_REQUEST, "TIER_NOT_FOUND");
        }
        d.setCompanyId(r.companyId());
        d.setTitle(r.title().trim());
        d.setRoleTitle(r.roleTitle().trim());
        d.setDriveType(r.driveType());
        d.setCtcLpa(r.ctcLpa());
        d.setStipendMonthly(r.stipendMonthly());
        d.setCtcBreakup(r.ctcBreakup());
        d.setLocation(r.location());
        d.setBondMonths(r.bondMonths() == null ? 0 : r.bondMonths());
        d.setBondDetails(r.bondDetails());
        d.setJobDescription(r.jobDescription());
        d.setSkills(r.skills() == null ? null : String.join(", ", r.skills()));
        d.setMode(r.mode() == null ? DriveMode.OFFLINE : r.mode());
        d.setVenue(r.venue());
        d.setRegistrationDeadline(r.registrationDeadline());
        d.setDriveDate(r.driveDate());
        d.setTierId(r.tierId());
        Eligibility e = r.eligibility();
        if (e != null) {
            Map<UUID, CollegeBranch> branches = directory.branches(collegeId);
            if (e.branchIds() != null && !branches.keySet().containsAll(e.branchIds())) {
                throw new DomainException("Unknown branch in eligibility", HttpStatus.BAD_REQUEST, "BRANCH_NOT_FOUND");
            }
            d.setMinCgpa(e.minCgpa());
            d.setMinTenth(e.minTenth());
            d.setMinTwelfth(e.minTwelfth());
            d.setMaxActiveBacklogs(e.maxActiveBacklogs());
            d.setMaxGapYears(e.maxGapYears());
            d.setBatchYears(e.batchYears() == null || e.batchYears().isEmpty() ? null : e.batchYears().stream().map(String::valueOf).collect(Collectors.joining(",")));
            d.setBranchIds(e.branchIds() == null || e.branchIds().isEmpty() ? null : e.branchIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
            d.setRequiredSkills(e.requiredSkills() == null || e.requiredSkills().isEmpty() ? null : String.join(", ", e.requiredSkills()));
        }
    }
}
