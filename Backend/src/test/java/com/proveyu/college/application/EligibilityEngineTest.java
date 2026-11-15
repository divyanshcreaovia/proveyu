package com.proveyu.college.application;

import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEngineTest {

    UUID cse = UUID.randomUUID(), ece = UUID.randomUUID();
    List<CollegeTier> tiers;
    CollegeProfile policy;
    CollegeDrive drive;
    CollegeStudent student;

    static BigDecimal d(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        tiers = List.of(
                tier("Normal", 1, "0"), tier("Dream", 2, "8"), tier("Super Dream", 3, "15"));
        policy = CollegeProfile.builder().policyMode(PolicyMode.TIER_UPGRADE).maxOffers(2).upgradeMinHikePercent(BigDecimal.ZERO).blockAfterAccept(true).build();
        drive = CollegeDrive.builder().ctcLpa(d("10")).minCgpa(d("7.0")).minTenth(d("60")).minTwelfth(d("60")).maxActiveBacklogs(0).maxGapYears(1)
                .branchIds(cse.toString()).batchYears("2027").requiredSkills("Java, SQL").build();
        drive.setId(UUID.randomUUID());
        student = CollegeStudent.builder().cgpa(d("8.1")).tenthPercent(d("85")).twelfthPercent(d("80")).activeBacklogs(0).gapYears(0)
                .branchId(cse).batchYear(2027).skills("java, sql, react").status(StudentStatus.ACTIVE).build();
        student.setId(UUID.randomUUID());
    }

    CollegeTier tier(String name, int rank, String min) {
        CollegeTier t = CollegeTier.builder().name(name).tierRank(rank).minCtcLpa(d(min)).build();
        t.setId(UUID.randomUUID());
        return t;
    }

    CollegeOffer offer(String ctc, int rank, OfferStatus status) {
        return CollegeOffer.builder().ctcLpa(d(ctc)).tierRank(rank).status(status).driveId(UUID.randomUUID()).build();
    }

    EligibilityEngine.Result eval(List<CollegeOffer> offers, EligibilityOverride o) {
        return EligibilityEngine.evaluate(drive, student, policy, tiers, offers, o);
    }

    @Test
    void eligibleWhenAllCriteriaMet() {
        assertThat(eval(List.of(), null).eligible()).isTrue();
    }

    @Test
    void reportsEveryFailedAcademicCriterion() {
        student.setCgpa(d("6.5"));
        student.setActiveBacklogs(2);
        student.setGapYears(3);
        student.setTenthPercent(null);
        var r = eval(List.of(), null);
        assertThat(r.eligible()).isFalse();
        assertThat(r.reasons()).anyMatch(s -> s.startsWith("CGPA")).anyMatch(s -> s.startsWith("Active backlogs"))
                .anyMatch(s -> s.startsWith("Gap years")).anyMatch(s -> s.startsWith("10th"));
    }

    @Test
    void branchBatchAndSkillsAreChecked() {
        student.setBranchId(ece);
        student.setBatchYear(2026);
        student.setSkills("java");
        var r = eval(List.of(), null);
        assertThat(r.reasons()).contains("Branch not eligible for this drive", "Batch 2026 not eligible", "Missing skills: sql");
    }

    @Test
    void nullCriteriaAreNotApplied() {
        drive = CollegeDrive.builder().ctcLpa(d("5")).build();
        student.setCgpa(null);
        student.setActiveBacklogs(5);
        assertThat(eval(List.of(), null).eligible()).isTrue();
    }

    @Test
    void debarredAndOptedOutStudentsAreIneligible() {
        student.setStatus(StudentStatus.DEBARRED);
        assertThat(eval(List.of(), null).reasons()).contains("Student is debarred");
        student.setStatus(StudentStatus.OPTED_OUT);
        assertThat(eval(List.of(), null).reasons()).contains("Student opted out of placements");
    }

    @Nested
    class PlacementPolicy {
        @Test
        void tierUpgradeAllowsHigherTierOnly() {
            var normalOffer = offer("4.5", 1, OfferStatus.ACCEPTED);
            assertThat(eval(List.of(normalOffer), null).eligible()).as("Normal -> Dream allowed").isTrue();
            var dreamOffer = offer("9", 2, OfferStatus.ACCEPTED);
            assertThat(eval(List.of(dreamOffer), null).reasons()).anyMatch(s -> s.contains("tier must be higher"));
        }

        @Test
        void tierUpgradeHonoursMinimumHike() {
            policy.setUpgradeMinHikePercent(d("50"));
            var r = eval(List.of(offer("7.5", 1, OfferStatus.ACCEPTED)), null);
            assertThat(r.eligible()).isFalse();
            assertThat(r.reasons()).anyMatch(s -> s.contains("CTC must be at least 11.25"));
        }

        @Test
        void oneOfferBlocksAnyActiveOffer() {
            policy.setPolicyMode(PolicyMode.ONE_OFFER);
            assertThat(eval(List.of(offer("4", 1, OfferStatus.OFFERED)), null).eligible()).isFalse();
            assertThat(eval(List.of(offer("4", 1, OfferStatus.DECLINED)), null).eligible()).as("declined offers do not count").isTrue();
        }

        @Test
        void openModeStillEnforcesMaxOffers() {
            policy.setPolicyMode(PolicyMode.OPEN);
            assertThat(eval(List.of(offer("12", 2, OfferStatus.ACCEPTED)), null).eligible()).isTrue();
            var r = eval(List.of(offer("12", 2, OfferStatus.ACCEPTED), offer("16", 3, OfferStatus.OFFERED)), null);
            assertThat(r.reasons()).anyMatch(s -> s.contains("policy allows at most 2"));
        }

        @Test
        void offerFromSameDriveDoesNotBlockItself() {
            CollegeOffer own = offer("10", 2, OfferStatus.ACCEPTED);
            own.setDriveId(drive.getId());
            assertThat(eval(List.of(own), null).eligible()).isTrue();
        }
    }

    @Test
    void overridesWin() {
        student.setCgpa(d("5"));
        var include = EligibilityOverride.builder().action(OverrideAction.INCLUDE).reason("Dean approved").build();
        var r = eval(List.of(), include);
        assertThat(r.eligible()).isTrue();
        assertThat(r.overridden()).isTrue();

        student.setCgpa(d("9"));
        var exclude = EligibilityOverride.builder().action(OverrideAction.EXCLUDE).reason("Disciplinary").build();
        r = eval(List.of(), exclude);
        assertThat(r.eligible()).isFalse();
        assertThat(r.reasons().get(0)).contains("Disciplinary");
    }

    @Test
    void tierDerivedFromCtc() {
        assertThat(EligibilityEngine.tierFor(d("3"), tiers).getName()).isEqualTo("Normal");
        assertThat(EligibilityEngine.tierFor(d("8"), tiers).getName()).isEqualTo("Dream");
        assertThat(EligibilityEngine.tierFor(d("22"), tiers).getName()).isEqualTo("Super Dream");
        drive.setTierId(tiers.get(2).getId());
        assertThat(EligibilityEngine.driveTierRank(drive, tiers)).as("explicit tier overrides CTC").isEqualTo(3);
    }
}
