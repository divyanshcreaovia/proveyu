package com.proveyu.placement.application;

import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlacementEligibilityEngineTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private DriveEligibilityRepository eligibilityRepository;
    @Mock
    private DriveTierRepository driveTierRepository;
    @Mock
    private DriveBranchRepository driveBranchRepository;
    @Mock
    private PlacementTierService tierService;

    @InjectMocks
    private PlacementDriveService driveService;

    private UUID collegeId;
    private UUID driveId;
    private UUID branchCsId;
    private UUID branchMechId;
    private UUID tier1Id;
    private UUID tier2Id;
    private UUID tier3Id;

    private Tier tier1;
    private Tier tier2;
    private Tier tier3;

    @BeforeEach
    void setUp() {
        collegeId = UUID.randomUUID();
        driveId = UUID.randomUUID();
        branchCsId = UUID.randomUUID();
        branchMechId = UUID.randomUUID();

        tier1Id = UUID.randomUUID();
        tier2Id = UUID.randomUUID();
        tier3Id = UUID.randomUUID();

        tier1 = Tier.builder().tierId(tier1Id).collegeId(collegeId).tierLevel(1).tierName("Tier 1").minScore(new BigDecimal("80.00")).build();
        tier2 = Tier.builder().tierId(tier2Id).collegeId(collegeId).tierLevel(2).tierName("Tier 2").minScore(new BigDecimal("70.00")).build();
        tier3 = Tier.builder().tierId(tier3Id).collegeId(collegeId).tierLevel(3).tierName("Tier 3").minScore(new BigDecimal("60.00")).build();
    }

    @Test
    @DisplayName("Tier boundary: Score exactly 80, 70, 60 and below 60")
    void testTierBoundaries() {
        PlacementTierService realTierService = new PlacementTierService(mock(TierRepository.class));
        TierRepository repo = mock(TierRepository.class);
        when(repo.findByCollegeIdOrderByTierLevelAsc(collegeId)).thenReturn(List.of(tier1, tier2, tier3));
        realTierService = new PlacementTierService(repo);

        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("80.00"))).contains(tier1);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("85.50"))).contains(tier1);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("70.00"))).contains(tier2);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("79.99"))).contains(tier2);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("60.00"))).contains(tier3);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("69.99"))).contains(tier3);
        assertThat(realTierService.deriveTier(collegeId, new BigDecimal("59.99"))).isEmpty();
    }

    @Test
    @DisplayName("Eligibility: Filter by Drive Tiers (Only Tier 1 allowed)")
    void testEligibilityFilterByTier() {
        PlacementDrive drive = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("12.00"))
                .placedPolicy(PlacedPolicy.INCLUDE_ALL)
                .build();

        Student student80 = Student.builder().studentId(UUID.randomUUID()).collegeId(collegeId).score(new BigDecimal("80.00")).branchId(branchCsId).build();
        Student student70 = Student.builder().studentId(UUID.randomUUID()).collegeId(collegeId).score(new BigDecimal("70.00")).branchId(branchCsId).build();

        when(studentRepository.findByCollegeId(collegeId)).thenReturn(List.of(student80, student70));
        when(driveTierRepository.findByIdDriveId(driveId)).thenReturn(List.of(new DriveTier(new DriveTierId(driveId, tier1Id))));
        when(driveBranchRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(eligibilityRepository.findByDriveId(driveId)).thenReturn(Optional.empty());

        when(tierService.deriveTier(collegeId, new BigDecimal("80.00"))).thenReturn(Optional.of(tier1));
        when(tierService.deriveTier(collegeId, new BigDecimal("70.00"))).thenReturn(Optional.of(tier2));

        List<Student> eligible = driveService.evaluateEligibility(collegeId, drive);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.get(0).getStudentId()).isEqualTo(student80.getStudentId());
    }

    @Test
    @DisplayName("Eligibility: Filter by Branch (Only CS branch allowed)")
    void testEligibilityFilterByBranch() {
        PlacementDrive drive = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("6.00"))
                .placedPolicy(PlacedPolicy.INCLUDE_ALL)
                .build();

        Student studentCs = Student.builder().studentId(UUID.randomUUID()).collegeId(collegeId).branchId(branchCsId).build();
        Student studentMech = Student.builder().studentId(UUID.randomUUID()).collegeId(collegeId).branchId(branchMechId).build();

        when(studentRepository.findByCollegeId(collegeId)).thenReturn(List.of(studentCs, studentMech));
        when(driveTierRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(driveBranchRepository.findByIdDriveId(driveId)).thenReturn(List.of(new DriveBranch(new DriveBranchId(driveId, branchCsId))));
        when(eligibilityRepository.findByDriveId(driveId)).thenReturn(Optional.empty());

        List<Student> eligible = driveService.evaluateEligibility(collegeId, drive);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.get(0).getStudentId()).isEqualTo(studentCs.getStudentId());
    }

    @Test
    @DisplayName("Eligibility: Academic criteria (CGPA, 10th%, 12th%, backlogs, gap years)")
    void testAcademicCriteria() {
        PlacementDrive drive = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("6.00"))
                .placedPolicy(PlacedPolicy.INCLUDE_ALL)
                .build();

        DriveEligibility elig = DriveEligibility.builder()
                .driveId(driveId)
                .minCgpa(new BigDecimal("7.50"))
                .min10thPercent(new BigDecimal("70.00"))
                .min12thPercent(new BigDecimal("70.00"))
                .maxActiveBacklogs(0)
                .maxGapYears(1)
                .build();

        Student passing = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .cgpa(new BigDecimal("8.00"))
                .tenthPercent(new BigDecimal("85.00"))
                .twelfthPercent(new BigDecimal("78.00"))
                .activeBacklogs(0)
                .gapYears(0)
                .build();

        Student failingBacklogs = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .cgpa(new BigDecimal("8.50"))
                .tenthPercent(new BigDecimal("90.00"))
                .twelfthPercent(new BigDecimal("85.00"))
                .activeBacklogs(1) // fail
                .gapYears(0)
                .build();

        Student failingCgpa = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .cgpa(new BigDecimal("7.20")) // fail
                .tenthPercent(new BigDecimal("85.00"))
                .twelfthPercent(new BigDecimal("78.00"))
                .activeBacklogs(0)
                .gapYears(0)
                .build();

        when(studentRepository.findByCollegeId(collegeId)).thenReturn(List.of(passing, failingBacklogs, failingCgpa));
        when(driveTierRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(driveBranchRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(eligibilityRepository.findByDriveId(driveId)).thenReturn(Optional.of(elig));

        List<Student> eligible = driveService.evaluateEligibility(collegeId, drive);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.get(0).getStudentId()).isEqualTo(passing.getStudentId());
    }

    @Test
    @DisplayName("Eligibility: Placed Policy EXCLUDE_ALL, INCLUDE_ALL, ONLY_IF_HIGHER_PACKAGE")
    void testPlacedPolicies() {
        Student unplaced = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .placedStatus(PlacedStatus.UNPLACED)
                .build();

        Student placedWith4Lpa = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .placedStatus(PlacedStatus.PLACED)
                .highestPackageLpa(new BigDecimal("4.00"))
                .build();

        Student placedWith10Lpa = Student.builder()
                .studentId(UUID.randomUUID())
                .collegeId(collegeId)
                .branchId(branchCsId)
                .placedStatus(PlacedStatus.PLACED)
                .highestPackageLpa(new BigDecimal("10.00"))
                .build();

        when(studentRepository.findByCollegeId(collegeId)).thenReturn(List.of(unplaced, placedWith4Lpa, placedWith10Lpa));
        when(driveTierRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(driveBranchRepository.findByIdDriveId(driveId)).thenReturn(Collections.emptyList());
        when(eligibilityRepository.findByDriveId(driveId)).thenReturn(Optional.empty());

        // 1. EXCLUDE_ALL
        PlacementDrive driveExclude = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("7.00"))
                .placedPolicy(PlacedPolicy.EXCLUDE_ALL)
                .build();
        List<Student> eligibleExclude = driveService.evaluateEligibility(collegeId, driveExclude);
        assertThat(eligibleExclude).hasSize(1);
        assertThat(eligibleExclude.get(0).getStudentId()).isEqualTo(unplaced.getStudentId());

        // 2. INCLUDE_ALL
        PlacementDrive driveInclude = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("7.00"))
                .placedPolicy(PlacedPolicy.INCLUDE_ALL)
                .build();
        List<Student> eligibleInclude = driveService.evaluateEligibility(collegeId, driveInclude);
        assertThat(eligibleInclude).hasSize(3);

        // 3. ONLY_IF_HIGHER_PACKAGE (Drive is 7 LPA -> 4 LPA is eligible, 10 LPA is not)
        PlacementDrive driveHigher = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("7.00"))
                .placedPolicy(PlacedPolicy.ONLY_IF_HIGHER_PACKAGE)
                .build();
        List<Student> eligibleHigher = driveService.evaluateEligibility(collegeId, driveHigher);
        assertThat(eligibleHigher).hasSize(2);
        assertThat(eligibleHigher).extracting(Student::getStudentId)
                .containsExactlyInAnyOrder(unplaced.getStudentId(), placedWith4Lpa.getStudentId());
    }
}
