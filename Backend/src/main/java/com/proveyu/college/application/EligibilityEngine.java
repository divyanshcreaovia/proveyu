package com.proveyu.college.application;

import com.proveyu.college.domain.CollegeDrive;
import com.proveyu.college.domain.CollegeEnums.OfferStatus;
import com.proveyu.college.domain.CollegeEnums.OverrideAction;
import com.proveyu.college.domain.CollegeEnums.PolicyMode;
import com.proveyu.college.domain.CollegeEnums.StudentStatus;
import com.proveyu.college.domain.CollegeOffer;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.domain.CollegeTier;
import com.proveyu.college.domain.EligibilityOverride;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure (side-effect free) eligibility + placement-policy engine.
 * <p>
 * A student is eligible for a drive when every academic criterion set on the drive is met
 * (NULL criterion = not applied), the student's branch / batch / skills match, the student is not
 * DEBARRED or OPTED_OUT, and the college placement policy allows another offer:
 * <ul>
 *   <li>OPEN: no restriction besides max offers.</li>
 *   <li>ONE_OFFER: a student holding an active offer (OFFERED / ACCEPTED / JOINED) is not eligible.</li>
 *   <li>TIER_UPGRADE ("dream policy"): a student with an active offer may apply only to a drive of a strictly
 *       higher tier whose CTC is at least highest CTC x (1 + upgradeMinHikePercent/100).</li>
 *   <li>If blockAfterAccept, a student who ACCEPTED or JOINED an offer is blocked under every mode except OPEN.</li>
 * </ul>
 * Manual overrides win: INCLUDE forces eligible, EXCLUDE forces ineligible; both carry an audited reason.
 */
public final class EligibilityEngine {

    private EligibilityEngine() {}

    public record Result(boolean eligible, List<String> reasons, boolean overridden) {}

    public static final Set<OfferStatus> ACTIVE_OFFER = Set.of(OfferStatus.OFFERED, OfferStatus.ACCEPTED, OfferStatus.JOINED);

    /** Tier of a CTC = best tier whose min CTC is <= ctc (tiers ordered by rank ascending, rank 1 = lowest). */
    public static CollegeTier tierFor(BigDecimal ctc, List<CollegeTier> tiers) {
        if (ctc == null) return null;
        return tiers.stream()
                .filter(t -> t.getMinCtcLpa() != null && ctc.compareTo(t.getMinCtcLpa()) >= 0)
                .max(Comparator.comparing(CollegeTier::getTierRank))
                .orElse(null);
    }

    public static Integer driveTierRank(CollegeDrive drive, List<CollegeTier> tiers) {
        if (drive.getTierId() != null) {
            return tiers.stream().filter(t -> t.getId().equals(drive.getTierId())).map(CollegeTier::getTierRank).findFirst().orElse(null);
        }
        CollegeTier t = tierFor(drive.getCtcLpa(), tiers);
        return t == null ? null : t.getTierRank();
    }

    public static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public static Result evaluate(CollegeDrive drive, CollegeStudent s, CollegeProfile policy, List<CollegeTier> tiers,
                                  List<CollegeOffer> studentOffers, EligibilityOverride override) {
        List<String> reasons = new ArrayList<>();

        if (s.getDeletedAt() != null) reasons.add("Student record is deleted");
        if (s.getStatus() == StudentStatus.DEBARRED) reasons.add("Student is debarred");
        if (s.getStatus() == StudentStatus.OPTED_OUT) reasons.add("Student opted out of placements");

        if (drive.getMinCgpa() != null && (s.getCgpa() == null || s.getCgpa().compareTo(drive.getMinCgpa()) < 0))
            reasons.add("CGPA " + fmt(s.getCgpa()) + " < required " + fmt(drive.getMinCgpa()));
        if (drive.getMinTenth() != null && (s.getTenthPercent() == null || s.getTenthPercent().compareTo(drive.getMinTenth()) < 0))
            reasons.add("10th % " + fmt(s.getTenthPercent()) + " < required " + fmt(drive.getMinTenth()));
        if (drive.getMinTwelfth() != null && (s.getTwelfthPercent() == null || s.getTwelfthPercent().compareTo(drive.getMinTwelfth()) < 0))
            reasons.add("12th % " + fmt(s.getTwelfthPercent()) + " < required " + fmt(drive.getMinTwelfth()));
        if (drive.getMaxActiveBacklogs() != null && s.getActiveBacklogs() > drive.getMaxActiveBacklogs())
            reasons.add("Active backlogs " + s.getActiveBacklogs() + " > allowed " + drive.getMaxActiveBacklogs());
        if (drive.getMaxGapYears() != null && s.getGapYears() > drive.getMaxGapYears())
            reasons.add("Gap years " + s.getGapYears() + " > allowed " + drive.getMaxGapYears());

        Set<String> branches = csv(drive.getBranchIds());
        if (!branches.isEmpty() && (s.getBranchId() == null || !branches.contains(s.getBranchId().toString().toLowerCase(Locale.ROOT))))
            reasons.add("Branch not eligible for this drive");
        Set<String> batches = csv(drive.getBatchYears());
        if (!batches.isEmpty() && (s.getBatchYear() == null || !batches.contains(String.valueOf(s.getBatchYear()))))
            reasons.add("Batch " + s.getBatchYear() + " not eligible");
        Set<String> required = csv(drive.getRequiredSkills());
        if (!required.isEmpty()) {
            Set<String> have = csv(s.getSkills());
            List<String> missing = required.stream().filter(r -> !have.contains(r)).sorted().toList();
            if (!missing.isEmpty()) reasons.add("Missing skills: " + String.join(", ", missing));
        }

        reasons.addAll(policyViolations(drive, policy, tiers, studentOffers));

        if (override != null) {
            if (override.getAction() == OverrideAction.INCLUDE) {
                return new Result(true, List.of("Included by override: " + override.getReason()), true);
            }
            List<String> r = new ArrayList<>(reasons);
            r.add(0, "Excluded by override: " + override.getReason());
            return new Result(false, r, true);
        }
        return new Result(reasons.isEmpty(), reasons, false);
    }

    /** Placement-policy checks only (used both by eligibility and when recording an offer). */
    public static List<String> policyViolations(CollegeDrive drive, CollegeProfile policy, List<CollegeTier> tiers, List<CollegeOffer> studentOffers) {
        List<String> reasons = new ArrayList<>();
        if (policy == null) return reasons;
        List<CollegeOffer> active = studentOffers.stream()
                .filter(o -> ACTIVE_OFFER.contains(o.getStatus()))
                .filter(o -> drive == null || drive.getId() == null || !drive.getId().equals(o.getDriveId()))
                .toList();
        if (active.isEmpty()) return reasons;

        if (active.size() >= policy.getMaxOffers()) {
            reasons.add("Already holds " + active.size() + " offer(s); policy allows at most " + policy.getMaxOffers());
        }
        if (policy.getPolicyMode() == PolicyMode.OPEN) return reasons;

        boolean accepted = active.stream().anyMatch(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED);
        if (policy.isBlockAfterAccept() && accepted && policy.getPolicyMode() == PolicyMode.ONE_OFFER) {
            reasons.add("Has accepted an offer (one-offer policy)");
            return reasons;
        }
        if (policy.getPolicyMode() == PolicyMode.ONE_OFFER) {
            reasons.add("Already placed (one-offer policy)");
            return reasons;
        }
        // TIER_UPGRADE
        if (drive == null) return reasons;
        int bestRank = active.stream().map(o -> o.getTierRank() == null ? 0 : o.getTierRank()).max(Integer::compare).orElse(0);
        BigDecimal bestCtc = active.stream().map(CollegeOffer::getCtcLpa).filter(c -> c != null).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        Integer driveRank = driveTierRank(drive, tiers);
        if (driveRank == null || driveRank <= bestRank) {
            reasons.add("Dream policy: drive tier must be higher than current offer tier");
        }
        BigDecimal hike = policy.getUpgradeMinHikePercent() == null ? BigDecimal.ZERO : policy.getUpgradeMinHikePercent();
        BigDecimal minCtc = bestCtc.multiply(BigDecimal.ONE.add(hike.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        boolean ctcTooLow = drive.getCtcLpa() == null || (hike.signum() > 0 && drive.getCtcLpa().compareTo(minCtc) < 0);
        if (ctcTooLow) {
            reasons.add("Dream policy: CTC must be at least " + fmt(minCtc.setScale(2, RoundingMode.HALF_UP)) + " LPA");
        }
        // Under TIER_UPGRADE an accepted offer does not block a genuine upgrade.
        return reasons;
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "n/a" : v.stripTrailingZeros().toPlainString();
    }
}
