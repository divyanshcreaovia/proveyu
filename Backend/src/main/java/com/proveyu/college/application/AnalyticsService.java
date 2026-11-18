package com.proveyu.college.application;

import com.proveyu.college.legacy.SkillRepository;
import com.proveyu.college.legacy.CandidateScore;
import com.proveyu.college.legacy.CandidateScoreRepository;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Dashboard KPIs, chart series, funnel and skill-gap analytics computed from tenant data. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CollegeStudentRepository studentRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeDriveRepository driveRepository;
    private final InterviewSlotRepository slotRepository;
    private final RoundResultRepository resultRepository;
    private final CollegeDirectory directory;
    private final CandidateScoreRepository scoreRepository;
    private final SkillRepository skillRepository;

    public record Kpis(long totalStudents, long eligibleStudents, long registeredStudents, long placedStudents, double placementPercent,
                       BigDecimal avgCtc, BigDecimal medianCtc, BigDecimal highestCtc, long activeDrives, long upcomingInterviews,
                       long pendingOffers, long totalOffers) {}

    public record BranchStat(String branch, String code, long total, long placed, double percent) {}

    public record Bucket(String label, long count) {}

    public record MonthPoint(String month, long offers, long placed) {}

    public record FunnelStage(String stage, long count) {}

    public record Dashboard(Kpis kpis, List<BranchStat> branchWise, List<Bucket> ctcDistribution, List<Bucket> companyWise,
                            List<MonthPoint> monthlyTrend, List<FunnelStage> funnel, List<Bucket> tierWise, List<Integer> batches) {}

    public record SkillGapRow(String skill, long demand, long supply, Double avgVerifiedScore, double gapIndex) {}

    record Scope(List<CollegeStudent> students, Set<UUID> studentIds, List<CollegeOffer> offers) {}

    Scope scope(CollegeContext ctx, Integer batchYear, LocalDate from, LocalDate to) {
        List<CollegeStudent> students = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> batchYear == null || batchYear.equals(s.getBatchYear())).toList();
        Set<UUID> ids = students.stream().map(CollegeStudent::getId).collect(Collectors.toSet());
        List<CollegeOffer> offers = offerRepository.findAllByCollegeId(ctx.collegeId()).stream()
                .filter(o -> ids.contains(o.getStudentId()))
                .filter(o -> from == null || (o.getOfferDate() != null && !o.getOfferDate().isBefore(from)))
                .filter(o -> to == null || (o.getOfferDate() != null && !o.getOfferDate().isAfter(to))).toList();
        return new Scope(students, ids, offers);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(CollegeContext ctx, Integer batchYear, LocalDate from, LocalDate to) {
        Scope sc = scope(ctx, batchYear, from, to);
        List<CollegeStudent> students = sc.students();
        List<CollegeOffer> active = sc.offers().stream().filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).toList();
        Set<UUID> placedIds = active.stream().filter(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED)
                .map(CollegeOffer::getStudentId).collect(Collectors.toSet());
        long eligible = students.stream().filter(s -> s.getStatus() != StudentStatus.DEBARRED && s.getStatus() != StudentStatus.OPTED_OUT).count();
        List<CollegeApplication> apps = applicationRepository.findAllByCollegeId(ctx.collegeId()).stream()
                .filter(a -> sc.studentIds().contains(a.getStudentId())).toList();
        Set<UUID> registered = apps.stream().filter(a -> a.getStatus() != ApplicationStatus.WITHDRAWN).map(CollegeApplication::getStudentId).collect(Collectors.toSet());

        // per-student best CTC among active offers
        Map<UUID, BigDecimal> best = new HashMap<>();
        active.forEach(o -> best.merge(o.getStudentId(), o.getCtcLpa(), BigDecimal::max));
        List<BigDecimal> ctcs = best.values().stream().sorted().toList();
        BigDecimal avg = ctcs.isEmpty() ? null : ctcs.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ctcs.size()), 2, RoundingMode.HALF_UP);
        BigDecimal median = median(ctcs);
        BigDecimal highest = ctcs.isEmpty() ? null : ctcs.get(ctcs.size() - 1);

        Instant now = Instant.now();
        long upcoming = slotRepository.findAllByCollegeIdAndStartsAtBetweenOrderByStartsAtAsc(ctx.collegeId(), now, now.plus(Duration.ofDays(7))).stream()
                .filter(s -> s.getStatus() == SlotStatus.SCHEDULED).count();
        long activeDrives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(d -> d.getStatus() == DriveStatus.PUBLISHED || d.getStatus() == DriveStatus.ONGOING).count();
        long pending = sc.offers().stream().filter(o -> o.getStatus() == OfferStatus.OFFERED).count();
        Kpis kpis = new Kpis(students.size(), eligible, registered.size(), placedIds.size(), pct(placedIds.size(), eligible), avg, median, highest,
                activeDrives, upcoming, pending, active.size());

        // branch-wise
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        List<BranchStat> branchWise = students.stream().collect(Collectors.groupingBy(s -> Optional.ofNullable(s.getBranchId()).map(UUID::toString).orElse("-")))
                .entrySet().stream().map(e -> {
                    CollegeBranch b = "-".equals(e.getKey()) ? null : branches.get(UUID.fromString(e.getKey()));
                    long placed = e.getValue().stream().filter(s -> placedIds.contains(s.getId())).count();
                    long elig = e.getValue().stream().filter(s -> s.getStatus() != StudentStatus.DEBARRED && s.getStatus() != StudentStatus.OPTED_OUT).count();
                    return new BranchStat(b == null ? "Unassigned" : b.getName(), b == null ? "-" : b.getCode(), e.getValue().size(), placed, pct(placed, elig));
                }).sorted(Comparator.comparing(BranchStat::code)).toList();

        // CTC distribution
        String[] labels = {"< 3 LPA", "3-5 LPA", "5-8 LPA", "8-12 LPA", "12-20 LPA", "20+ LPA"};
        double[] edges = {3, 5, 8, 12, 20};
        long[] counts = new long[labels.length];
        for (BigDecimal c : ctcs) {
            int i = 0;
            while (i < edges.length && c.doubleValue() >= edges[i]) i++;
            counts[i]++;
        }
        List<Bucket> dist = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) dist.add(new Bucket(labels[i], counts[i]));

        // company-wise
        Map<UUID, CollegeCompany> companies = directory.companies(ctx.collegeId(), active.stream().map(CollegeOffer::getCompanyId).collect(Collectors.toSet()));
        List<Bucket> companyWise = active.stream().collect(Collectors.groupingBy(CollegeOffer::getCompanyId, Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).limit(10)
                .map(e -> new Bucket(Optional.ofNullable(companies.get(e.getKey())).map(CollegeCompany::getName).orElse("-"), e.getValue())).toList();

        // monthly trend (last 12 months, or the filter range)
        YearMonth end = to == null ? YearMonth.now() : YearMonth.from(to);
        YearMonth start = from == null ? end.minusMonths(11) : YearMonth.from(from);
        if (start.isAfter(end)) start = end;
        DateTimeFormatter mf = DateTimeFormatter.ofPattern("MMM yy");
        List<MonthPoint> trend = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end) && trend.size() < 36; m = m.plusMonths(1)) {
            YearMonth month = m;
            long offers = active.stream().filter(o -> o.getOfferDate() != null && YearMonth.from(o.getOfferDate()).equals(month)).count();
            long placed = active.stream().filter(o -> (o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED)
                    && o.getOfferDate() != null && YearMonth.from(o.getOfferDate()).equals(month)).count();
            trend.add(new MonthPoint(month.format(mf), offers, placed));
        }

        // funnel
        Set<UUID> appIds = apps.stream().map(CollegeApplication::getId).collect(Collectors.toSet());
        Map<UUID, UUID> appToStudent = apps.stream().collect(Collectors.toMap(CollegeApplication::getId, CollegeApplication::getStudentId));
        Set<UUID> shortlisted = apps.stream().filter(a -> EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.IN_PROCESS, ApplicationStatus.SELECTED)
                .contains(a.getStatus()) || a.getCurrentRound() > 0).map(CollegeApplication::getStudentId).collect(Collectors.toSet());
        Set<UUID> interviewed = new HashSet<>();
        if (!appIds.isEmpty()) {
            resultRepository.findAllByCollegeIdAndApplicationIdIn(ctx.collegeId(), appIds).stream()
                    .filter(r -> r.getResult() != RoundResultValue.PENDING && r.getResult() != RoundResultValue.ABSENT)
                    .forEach(r -> interviewed.add(appToStudent.get(r.getApplicationId())));
        }
        Set<UUID> offered = sc.offers().stream().filter(o -> o.getStatus() != OfferStatus.REVOKED).map(CollegeOffer::getStudentId).collect(Collectors.toSet());
        Set<UUID> joined = sc.offers().stream().filter(o -> o.getStatus() == OfferStatus.JOINED).map(CollegeOffer::getStudentId).collect(Collectors.toSet());
        List<FunnelStage> funnel = List.of(new FunnelStage("Registered", registered.size()), new FunnelStage("Shortlisted", shortlisted.size()),
                new FunnelStage("Interviewed", interviewed.size()), new FunnelStage("Offered", offered.size()), new FunnelStage("Joined", joined.size()));

        // tier-wise
        List<CollegeTier> tiers = directory.tiers(ctx.collegeId());
        List<Bucket> tierWise = tiers.stream().map(t -> new Bucket(t.getName(), active.stream().filter(o -> t.getTierRank().equals(o.getTierRank())).count())).toList();

        List<Integer> batches = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream().map(CollegeStudent::getBatchYear)
                .filter(Objects::nonNull).distinct().sorted(Comparator.reverseOrder()).toList();
        return new Dashboard(kpis, branchWise, dist, companyWise, trend, funnel, tierWise, batches);
    }

    @Transactional(readOnly = true)
    public List<SkillGapRow> skillGap(CollegeContext ctx, Integer batchYear) {
        Map<String, String> display = new HashMap<>();
        Map<String, Long> demand = new HashMap<>();
        Instant since = Instant.now().minus(Duration.ofDays(365));
        driveRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(d -> d.getStatus() != DriveStatus.DRAFT && d.getCreatedAt() != null && d.getCreatedAt().isAfter(since))
                .forEach(d -> {
                    Set<String> skills = new HashSet<>();
                    StudentService.splitSkills(d.getSkills()).forEach(s -> skills.add(norm(s, display)));
                    StudentService.splitSkills(d.getRequiredSkills()).forEach(s -> skills.add(norm(s, display)));
                    skills.forEach(s -> demand.merge(s, 1L, Long::sum));
                });
        Scope sc = scope(ctx, batchYear, null, null);
        Map<String, Long> supply = new HashMap<>();
        sc.students().stream().filter(s -> s.getStatus() == StudentStatus.ACTIVE)
                .forEach(s -> StudentService.splitSkills(s.getSkills()).stream().map(x -> norm(x, display)).distinct().forEach(x -> supply.merge(x, 1L, Long::sum)));
        // PROVEYU verified assessment scores per skill
        Map<String, Double> verified = new HashMap<>();
        Set<UUID> userIds = sc.students().stream().map(CollegeStudent::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            List<CandidateScore> scores = scoreRepository.findByCandidateIdIn(userIds);
            Map<UUID, String> names = new HashMap<>();
            skillRepository.findAllById(scores.stream().map(CandidateScore::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()))
                    .forEach(s -> names.put(s.getId(), s.getName()));
            scores.stream().filter(s -> s.getMaxScore() > 0 && names.containsKey(s.getSkillId()))
                    .collect(Collectors.groupingBy(s -> norm(names.get(s.getSkillId()), display), Collectors.averagingDouble(s -> s.getScore() * 100.0 / s.getMaxScore())))
                    .forEach((k, v) -> verified.put(k, Math.round(v * 10) / 10.0));
        }
        long activeStudents = Math.max(1, sc.students().stream().filter(s -> s.getStatus() == StudentStatus.ACTIVE).count());
        long totalDrives = Math.max(1, demand.values().stream().mapToLong(Long::longValue).max().orElse(1));
        Set<String> keys = new TreeSet<>(demand.keySet());
        keys.addAll(verified.keySet());
        return keys.stream().map(k -> {
            long d = demand.getOrDefault(k, 0L), s = supply.getOrDefault(k, 0L);
            double gap = Math.round(((double) d / totalDrives - (double) s / activeStudents) * 1000) / 10.0;
            return new SkillGapRow(display.getOrDefault(k, k), d, s, verified.get(k), gap);
        }).sorted(Comparator.comparingDouble(SkillGapRow::gapIndex).reversed()).limit(40).toList();
    }

    private static String norm(String s, Map<String, String> display) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        display.putIfAbsent(k, s.trim());
        return k;
    }

    static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    static BigDecimal median(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) return null;
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
