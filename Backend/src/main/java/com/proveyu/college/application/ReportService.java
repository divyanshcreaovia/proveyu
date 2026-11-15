package com.proveyu.college.application;

import com.proveyu.college.application.TabularExporter.Table;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Report catalogue (placement-summary [NIRF/NAAC style], branch, company, ctc, drive, alumni, skill-gap)
 * rendered to XLSX / CSV / PDF, plus scheduled e-mail delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("placement-summary", "Placement summary by batch & branch (NIRF / NAAC format)");
        TYPES.put("branch", "Branch-wise placement report");
        TYPES.put("company", "Company-wise recruitment report");
        TYPES.put("ctc", "CTC report (every placed student)");
        TYPES.put("drive", "Drive-wise report (all drives, or round-wise for one drive)");
        TYPES.put("alumni", "Alumni placement history (past batches)");
        TYPES.put("skill-gap", "Skill-gap analysis (drive demand vs student supply)");
    }

    public record ReportType(String key, String description) {}

    public record ScheduleRequest(@NotBlank String reportType, @NotNull ReportFormat format, @NotNull ReportFrequency frequency,
                                  @NotBlank @Size(max = 1000) @Pattern(regexp = "^[^@\\s,]+@[^@\\s,]+(\\s*,\\s*[^@\\s,]+@[^@\\s,]+)*$",
                                          message = "Comma separated e-mail addresses") String recipients, Integer batchYear) {}

    public record ScheduleResponse(UUID id, String reportType, ReportFormat format, ReportFrequency frequency, String recipients, boolean active,
                                   Instant lastRunAt, Instant nextRunAt) {}

    private final CollegeStudentRepository studentRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeDriveRepository driveRepository;
    private final DriveRoundRepository roundRepository;
    private final RoundResultRepository resultRepository;
    private final ScheduledReportRepository scheduleRepository;
    private final CollegeDirectory directory;
    private final AnalyticsService analyticsService;
    private final TabularExporter exporter;
    private final EmailService emailService;
    private final AuditService auditService;

    public List<ReportType> types() {
        return TYPES.entrySet().stream().map(e -> new ReportType(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public TabularExporter.Export export(CollegeContext ctx, String type, String format, Integer batchYear, UUID driveId) {
        return exporter.export(build(ctx, type, batchYear, driveId), format, type + "-report");
    }

    @Transactional(readOnly = true)
    public Table build(CollegeContext ctx, String type, Integer batchYear, UUID driveId) {
        return switch (type) {
            case "placement-summary" -> placementSummary(ctx, batchYear);
            case "branch" -> branchReport(ctx, batchYear);
            case "company" -> companyReport(ctx, batchYear);
            case "ctc" -> ctcReport(ctx, batchYear);
            case "drive" -> driveId == null ? drivesReport(ctx) : driveRoundReport(ctx, driveId);
            case "alumni" -> alumniReport(ctx);
            case "skill-gap" -> skillGapReport(ctx, batchYear);
            default -> throw new DomainException("Unknown report type " + type, HttpStatus.BAD_REQUEST, "UNKNOWN_REPORT");
        };
    }

    // ---------------------------------------------------------------- data helpers

    private record Data(List<CollegeStudent> students, Map<UUID, List<CollegeOffer>> offersByStudent, Map<UUID, CollegeBranch> branches) {
        List<CollegeOffer> active(UUID studentId) {
            return offersByStudent.getOrDefault(studentId, List.of()).stream().filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).toList();
        }

        boolean placed(UUID studentId) {
            return active(studentId).stream().anyMatch(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED);
        }

        BigDecimal best(UUID studentId) {
            return active(studentId).stream().map(CollegeOffer::getCtcLpa).max(BigDecimal::compareTo).orElse(null);
        }
    }

    private Data data(CollegeContext ctx, Integer batchYear) {
        var students = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> batchYear == null || batchYear.equals(s.getBatchYear())).toList();
        Set<UUID> ids = students.stream().map(CollegeStudent::getId).collect(Collectors.toSet());
        var offers = offerRepository.findAllByCollegeId(ctx.collegeId()).stream().filter(o -> ids.contains(o.getStudentId()))
                .collect(Collectors.groupingBy(CollegeOffer::getStudentId));
        return new Data(students, offers, directory.branches(ctx.collegeId()));
    }

    private static List<Object> stats(String label1, Object label2, List<CollegeStudent> group, Data d) {
        long total = group.size();
        long optedOut = group.stream().filter(s -> s.getStatus() == StudentStatus.OPTED_OUT || s.getStatus() == StudentStatus.DEBARRED).count();
        long eligible = total - optedOut;
        long placed = group.stream().filter(s -> d.placed(s.getId())).count();
        List<BigDecimal> ctc = group.stream().map(s -> d.best(s.getId())).filter(Objects::nonNull).sorted().toList();
        BigDecimal avg = ctc.isEmpty() ? null : ctc.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ctc.size()), 2, RoundingMode.HALF_UP);
        return Arrays.asList(label1, label2, total, optedOut, eligible, placed, AnalyticsService.pct(placed, eligible),
                blank(AnalyticsService.median(ctc)), blank(avg), blank(ctc.isEmpty() ? null : ctc.get(ctc.size() - 1)));
    }

    private static Object blank(Object o) {
        return o == null ? "" : o;
    }

    private String branchLabel(Data d, UUID id) {
        CollegeBranch b = id == null ? null : d.branches().get(id);
        return b == null ? "Unassigned" : b.getCode() + " - " + b.getName();
    }

    // ---------------------------------------------------------------- reports

    private Table placementSummary(CollegeContext ctx, Integer batchYear) {
        Data d = data(ctx, batchYear);
        List<List<Object>> rows = new ArrayList<>();
        d.students().stream().collect(Collectors.groupingBy(CollegeStudent::getBatchYear, TreeMap::new, Collectors.toList())).forEach((batch, list) -> {
            list.stream().collect(Collectors.groupingBy(s -> branchLabel(d, s.getBranchId()), TreeMap::new, Collectors.toList()))
                    .forEach((branch, g) -> rows.add(stats(String.valueOf(batch), branch, g, d)));
            rows.add(stats(batch + " TOTAL", "All branches", list, d));
        });
        if (!d.students().isEmpty()) rows.add(stats("GRAND TOTAL", "", d.students(), d));
        CollegeProfile p = directory.profile(ctx.collegeId());
        return new Table(p.getCollegeName() + " - Placement Summary", List.of("Batch", "Branch", "Total students", "Opted out / debarred",
                "Eligible", "Placed", "Placement %", "Median CTC (LPA)", "Average CTC (LPA)", "Highest CTC (LPA)"), rows);
    }

    private Table branchReport(CollegeContext ctx, Integer batchYear) {
        Data d = data(ctx, batchYear);
        List<List<Object>> rows = new ArrayList<>();
        d.students().stream().collect(Collectors.groupingBy(s -> branchLabel(d, s.getBranchId()), TreeMap::new, Collectors.toList()))
                .forEach((branch, g) -> rows.add(stats(branch, batchYear == null ? "All" : batchYear, g, d)));
        return new Table("Branch-wise placement", List.of("Branch", "Batch", "Total", "Opted out / debarred", "Eligible", "Placed", "Placement %",
                "Median CTC", "Average CTC", "Highest CTC"), rows);
    }

    private Table companyReport(CollegeContext ctx, Integer batchYear) {
        Data d = data(ctx, batchYear);
        Set<UUID> ids = d.students().stream().map(CollegeStudent::getId).collect(Collectors.toSet());
        var offers = d.offersByStudent().values().stream().flatMap(List::stream).toList();
        var drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId());
        var apps = applicationRepository.findAllByCollegeId(ctx.collegeId()).stream().filter(a -> ids.contains(a.getStudentId())).toList();
        Set<UUID> companyIds = new HashSet<>();
        drives.forEach(x -> companyIds.add(x.getCompanyId()));
        offers.forEach(o -> companyIds.add(o.getCompanyId()));
        var companies = directory.companies(ctx.collegeId(), companyIds);
        List<List<Object>> rows = companyIds.stream().map(cid -> {
            var cDrives = drives.stream().filter(x -> x.getCompanyId().equals(cid)).map(CollegeDrive::getId).collect(Collectors.toSet());
            var cOffers = offers.stream().filter(o -> o.getCompanyId().equals(cid)).toList();
            var activeOffers = cOffers.stream().filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).toList();
            List<BigDecimal> ctc = activeOffers.stream().map(CollegeOffer::getCtcLpa).sorted().toList();
            BigDecimal avg = ctc.isEmpty() ? null : ctc.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ctc.size()), 2, RoundingMode.HALF_UP);
            return Arrays.<Object>asList(Optional.ofNullable(companies.get(cid)).map(CollegeCompany::getName).orElse("-"), cDrives.size(),
                    apps.stream().filter(a -> cDrives.contains(a.getDriveId())).count(), activeOffers.size(),
                    cOffers.stream().filter(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED).count(),
                    blank(avg), blank(ctc.isEmpty() ? null : ctc.get(ctc.size() - 1)));
        }).sorted(Comparator.comparing(r -> r.get(0).toString())).toList();
        return new Table("Company-wise recruitment", List.of("Company", "Drives", "Applicants", "Offers", "Accepted / joined", "Average CTC", "Highest CTC"), rows);
    }

    private Table ctcReport(CollegeContext ctx, Integer batchYear) {
        Data d = data(ctx, batchYear);
        var offers = d.offersByStudent().values().stream().flatMap(List::stream).filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).toList();
        var companies = directory.companies(ctx.collegeId(), offers.stream().map(CollegeOffer::getCompanyId).collect(Collectors.toSet()));
        Map<UUID, CollegeStudent> students = d.students().stream().collect(Collectors.toMap(CollegeStudent::getId, s -> s));
        var tiers = directory.tiers(ctx.collegeId());
        List<List<Object>> rows = offers.stream().sorted(Comparator.comparing(CollegeOffer::getCtcLpa).reversed()).map(o -> {
            CollegeStudent s = students.get(o.getStudentId());
            String tier = o.getTierRank() == null ? "" : tiers.stream().filter(t -> t.getTierRank().equals(o.getTierRank())).map(CollegeTier::getName).findFirst().orElse("");
            return Arrays.<Object>asList(s.getRollNumber(), CollegeDirectory.fullName(s), branchLabel(d, s.getBranchId()), s.getBatchYear(),
                    Optional.ofNullable(companies.get(o.getCompanyId())).map(CollegeCompany::getName).orElse("-"), o.getRoleTitle(), o.getCtcLpa(), tier,
                    o.getStatus().name(), blank(o.getOfferDate()));
        }).toList();
        return new Table("CTC report", List.of("Roll No", "Student", "Branch", "Batch", "Company", "Role", "CTC (LPA)", "Tier", "Status", "Offer date"), rows);
    }

    private Table drivesReport(CollegeContext ctx) {
        var drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId());
        var companies = directory.companies(ctx.collegeId(), drives.stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        var apps = applicationRepository.findAllByCollegeId(ctx.collegeId());
        var offers = offerRepository.findAllByCollegeId(ctx.collegeId());
        List<List<Object>> rows = drives.stream().sorted(Comparator.comparing(CollegeDrive::getCreatedAt).reversed()).map(d -> {
            var dApps = apps.stream().filter(a -> a.getDriveId().equals(d.getId())).toList();
            return Arrays.<Object>asList(d.getTitle(), Optional.ofNullable(companies.get(d.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                    d.getDriveType().name(), blank(d.getCtcLpa()), blank(d.getDriveDate()), d.getStatus().name(), dApps.size(),
                    dApps.stream().filter(a -> a.getStatus() != ApplicationStatus.APPLIED && a.getStatus() != ApplicationStatus.WITHDRAWN
                            && a.getStatus() != ApplicationStatus.REJECTED || a.getCurrentRound() > 0).count(),
                    dApps.stream().filter(a -> a.getStatus() == ApplicationStatus.SELECTED).count(),
                    offers.stream().filter(o -> d.getId().equals(o.getDriveId()) && EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).count());
        }).toList();
        return new Table("Drive-wise report", List.of("Drive", "Company", "Type", "CTC (LPA)", "Date", "Status", "Applicants", "Shortlisted",
                "Selected", "Offers"), rows);
    }

    private Table driveRoundReport(CollegeContext ctx, UUID driveId) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), driveId);
        var rounds = roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), driveId);
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), driveId);
        var students = directory.students(ctx.collegeId(), apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        Map<UUID, Map<UUID, RoundResult>> results = new HashMap<>();
        if (!apps.isEmpty()) {
            resultRepository.findAllByCollegeIdAndApplicationIdIn(ctx.collegeId(), apps.stream().map(CollegeApplication::getId).toList())
                    .forEach(r -> results.computeIfAbsent(r.getApplicationId(), k -> new HashMap<>()).put(r.getRoundId(), r));
        }
        var branches = directory.branches(ctx.collegeId());
        List<String> headers = new ArrayList<>(List.of("Roll No", "Student", "Branch", "Application status"));
        rounds.forEach(r -> headers.add("R" + r.getSeq() + " " + r.getName()));
        List<List<Object>> rows = apps.stream().map(a -> {
            CollegeStudent s = students.get(a.getStudentId());
            if (s == null || !ctx.canAccessBranch(s.getBranchId())) return null;
            CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
            List<Object> row = new ArrayList<>(List.of(s.getRollNumber(), CollegeDirectory.fullName(s), b == null ? "" : b.getCode(), a.getStatus().name()));
            rounds.forEach(r -> {
                RoundResult rr = results.getOrDefault(a.getId(), Map.of()).get(r.getId());
                row.add(rr == null ? "-" : rr.getResult().name() + (rr.getScore() == null ? "" : " (" + rr.getScore().stripTrailingZeros().toPlainString() + ")"));
            });
            return row;
        }).filter(Objects::nonNull).sorted(Comparator.comparing(r -> r.get(0).toString())).toList();
        return new Table(drive.getTitle() + " - round-wise report", headers, rows);
    }

    private Table alumniReport(CollegeContext ctx) {
        int current = Year.now().getValue();
        Data d = data(ctx, null);
        var past = d.students().stream().filter(s -> s.getBatchYear() != null && s.getBatchYear() < current).toList();
        var offers = past.stream().flatMap(s -> d.offersByStudent().getOrDefault(s.getId(), List.of()).stream()).toList();
        var companies = directory.companies(ctx.collegeId(), offers.stream().map(CollegeOffer::getCompanyId).collect(Collectors.toSet()));
        List<List<Object>> rows = new ArrayList<>();
        past.stream().sorted(Comparator.comparing(CollegeStudent::getBatchYear).reversed().thenComparing(CollegeStudent::getRollNumber)).forEach(s -> {
            var so = d.offersByStudent().getOrDefault(s.getId(), List.of()).stream().filter(o -> o.getStatus() == OfferStatus.JOINED || o.getStatus() == OfferStatus.ACCEPTED)
                    .max(Comparator.comparing(CollegeOffer::getCtcLpa));
            rows.add(Arrays.asList(s.getBatchYear(), s.getRollNumber(), CollegeDirectory.fullName(s), branchLabel(d, s.getBranchId()), s.getEmail(),
                    so.map(o -> Optional.ofNullable(companies.get(o.getCompanyId())).map(CollegeCompany::getName).orElse("-")).orElse("Not placed"),
                    so.map(CollegeOffer::getRoleTitle).orElse(""), so.map(o -> (Object) o.getCtcLpa()).orElse(""),
                    so.map(o -> o.getStatus().name()).orElse(""), so.map(o -> (Object) blank(o.getJoiningDate())).orElse("")));
        });
        return new Table("Alumni placement history", List.of("Batch", "Roll No", "Name", "Branch", "Email", "Company", "Role", "CTC (LPA)", "Status",
                "Joining date"), rows);
    }

    private Table skillGapReport(CollegeContext ctx, Integer batchYear) {
        List<List<Object>> rows = analyticsService.skillGap(ctx, batchYear).stream()
                .map(r -> Arrays.<Object>asList(r.skill(), r.demand(), r.supply(), blank(r.avgVerifiedScore()), r.gapIndex())).toList();
        return new Table("Skill-gap analysis", List.of("Skill", "Drives requiring", "Students with skill", "Avg PROVEYU verified score %", "Gap index"), rows);
    }

    // ---------------------------------------------------------------- schedules

    @Transactional(readOnly = true)
    public List<ScheduleResponse> schedules(CollegeContext ctx) {
        return scheduleRepository.findAllByCollegeIdOrderByCreatedAtDesc(ctx.collegeId()).stream().map(this::toSchedule).toList();
    }

    @Transactional
    public ScheduleResponse createSchedule(CollegeContext ctx, ScheduleRequest r) {
        if (!TYPES.containsKey(r.reportType())) throw new DomainException("Unknown report type", HttpStatus.BAD_REQUEST, "UNKNOWN_REPORT");
        ScheduledReport s = scheduleRepository.save(ScheduledReport.builder().collegeId(ctx.collegeId()).reportType(r.reportType())
                .format(r.format()).frequency(r.frequency()).recipients(r.recipients().replaceAll("\\s", "")).createdBy(ctx.userId())
                .nextRunAt(next(Instant.now(), r.frequency())).build());
        auditService.log(ctx, "REPORT_SCHEDULED", "REPORT", s.getId(), "Scheduled " + r.frequency() + " " + r.reportType() + " report", r.recipients());
        return toSchedule(s);
    }

    @Transactional
    public ScheduleResponse toggleSchedule(CollegeContext ctx, UUID id, boolean active) {
        ScheduledReport s = scheduleRepository.findByIdAndCollegeId(id, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Schedule"));
        s.setActive(active);
        if (active && s.getNextRunAt().isBefore(Instant.now())) s.setNextRunAt(next(Instant.now(), s.getFrequency()));
        return toSchedule(scheduleRepository.save(s));
    }

    @Transactional
    public void deleteSchedule(CollegeContext ctx, UUID id) {
        ScheduledReport s = scheduleRepository.findByIdAndCollegeId(id, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Schedule"));
        scheduleRepository.delete(s);
        auditService.log(ctx, "REPORT_SCHEDULE_DELETED", "REPORT", id, "Deleted scheduled " + s.getReportType() + " report", null);
    }

    /** Runs due schedules (every 10 minutes) and e-mails the report as an attachment. */
    @Scheduled(fixedDelayString = "${college.reports.interval-ms:600000}", initialDelay = 90000)
    @Transactional
    public void runDueSchedules() {
        Instant now = Instant.now();
        for (ScheduledReport s : scheduleRepository.findAllByActiveTrueAndNextRunAtBefore(now)) {
            try {
                CollegeContext system = new CollegeContext(s.getCollegeId(), null, null, MemberRole.COLLEGE_ADMIN, Set.of(), "Scheduler");
                var export = export(system, s.getReportType(), s.getFormat().name().toLowerCase(Locale.ROOT), null, null);
                String college = directory.profile(s.getCollegeId()).getCollegeName();
                for (String to : s.getRecipients().split(",")) {
                    emailService.sendHtml(to.trim(), college + " - " + TYPES.get(s.getReportType()), "<p>Attached is your scheduled "
                            + s.getFrequency().name().toLowerCase(Locale.ROOT) + " report.</p>", export.fileName(), export.bytes(), export.contentType());
                }
                s.setLastRunAt(now);
            } catch (Exception e) {
                log.warn("[SCHEDULED REPORT] {} failed: {}", s.getId(), e.getMessage());
            }
            s.setNextRunAt(next(now, s.getFrequency()));
            scheduleRepository.save(s);
        }
    }

    static Instant next(Instant from, ReportFrequency f) {
        ZonedDateTime z = from.atZone(ZoneId.of("Asia/Kolkata")).withHour(8).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime n = switch (f) {
            case DAILY -> z.plusDays(1);
            case WEEKLY -> z.plusWeeks(1).with(DayOfWeek.MONDAY);
            case MONTHLY -> z.plusMonths(1).withDayOfMonth(1);
        };
        return n.toInstant();
    }

    private ScheduleResponse toSchedule(ScheduledReport s) {
        return new ScheduleResponse(s.getId(), s.getReportType(), s.getFormat(), s.getFrequency(), s.getRecipients(), s.isActive(), s.getLastRunAt(), s.getNextRunAt());
    }
}
