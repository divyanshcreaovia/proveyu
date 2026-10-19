package com.proveyu.placement.application;

import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import com.proveyu.placement.web.dto.PlacementDtos.BulkUpdateRoundStudentsRequest;
import com.proveyu.placement.web.dto.PlacementDtos.DriveRoundResponse;
import com.proveyu.placement.web.dto.PlacementDtos.UpdateRoundStudentItem;
import com.proveyu.shared.error.DomainException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriveRoundAdvancementTest {

    @Mock
    private DriveRoundRepository driveRoundRepository;
    @Mock
    private DriveRoundStudentRepository driveRoundStudentRepository;
    @Mock
    private PlacementDriveRepository driveRepository;
    @Mock
    private HiringRoundRepository hiringRoundRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private com.proveyu.auth.infrastructure.UserRepository userRepository;

    @InjectMocks
    private DriveRoundService roundService;

    private UUID collegeId;
    private UUID driveId;
    private UUID round1Id;
    private UUID round3Id;
    private UUID round5Id;
    private UUID student1Id;
    private UUID student2Id;

    private DriveRound driveRound1;
    private DriveRound driveRound3;
    private DriveRound driveRound5;
    private PlacementDrive drive;

    @BeforeEach
    void setUp() {
        collegeId = UUID.randomUUID();
        driveId = UUID.randomUUID();
        round1Id = UUID.randomUUID();
        round3Id = UUID.randomUUID();
        round5Id = UUID.randomUUID();
        student1Id = UUID.randomUUID();
        student2Id = UUID.randomUUID();

        drive = PlacementDrive.builder()
                .driveId(driveId)
                .collegeId(collegeId)
                .offeredPackage(new BigDecimal("10.00"))
                .build();

        driveRound1 = DriveRound.builder()
                .driveRoundId(round1Id)
                .driveId(driveId)
                .roundNumber(1)
                .roundId(UUID.randomUUID())
                .status(RoundStatus.ONGOING)
                .build();

        driveRound3 = DriveRound.builder()
                .driveRoundId(round3Id)
                .driveId(driveId)
                .roundNumber(3) // Note: skipped round 2!
                .roundId(UUID.randomUUID())
                .status(RoundStatus.PENDING)
                .build();

        driveRound5 = DriveRound.builder()
                .driveRoundId(round5Id)
                .driveId(driveId)
                .roundNumber(5)
                .roundId(UUID.randomUUID())
                .status(RoundStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Round advance: Skipped rounds (Round 1 advances to Round 3)")
    void testAdvanceWithSkippedRounds() {
        when(driveRoundRepository.findById(round1Id)).thenReturn(Optional.of(driveRound1));
        when(driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)).thenReturn(Optional.of(drive));
        when(driveRoundRepository.findByDriveIdOrderByRoundNumberAsc(driveId))
                .thenReturn(List.of(driveRound1, driveRound3, driveRound5));

        DriveRoundStudent drs1 = DriveRoundStudent.builder()
                .id(UUID.randomUUID())
                .driveRoundId(round1Id)
                .studentId(student1Id)
                .status(RoundStudentStatus.SELECTED)
                .build();

        when(driveRoundStudentRepository.findByDriveRoundIdAndStatus(round1Id, RoundStudentStatus.SELECTED))
                .thenReturn(List.of(drs1));

        when(driveRoundStudentRepository.existsByDriveRoundIdAndStudentId(round3Id, student1Id)).thenReturn(false);
        when(hiringRoundRepository.findById(any())).thenReturn(Optional.of(new HiringRound(UUID.randomUUID(), "Technical Round")));

        DriveRoundResponse response = roundService.advanceRound(collegeId, round1Id);

        assertThat(response).isNotNull();
        assertThat(response.getRoundNumber()).isEqualTo(3);
        verify(driveRoundStudentRepository).save(argThat(savedDrs ->
                savedDrs.getDriveRoundId().equals(round3Id) &&
                savedDrs.getStudentId().equals(student1Id) &&
                savedDrs.getStatus() == RoundStudentStatus.SHORTLISTED
        ));
    }

    @Test
    @DisplayName("Transition validation: SHORTLISTED directly to SELECTED must be rejected")
    void testDirectSelectionFromShortlistBlocked() {
        when(driveRoundRepository.findById(round1Id)).thenReturn(Optional.of(driveRound1));
        when(driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)).thenReturn(Optional.of(drive));

        DriveRoundStudent drs = DriveRoundStudent.builder()
                .id(UUID.randomUUID())
                .driveRoundId(round1Id)
                .studentId(student1Id)
                .status(RoundStudentStatus.SHORTLISTED)
                .build();

        when(driveRoundStudentRepository.findByDriveRoundIdAndStudentId(round1Id, student1Id))
                .thenReturn(Optional.of(drs));

        BulkUpdateRoundStudentsRequest request = BulkUpdateRoundStudentsRequest.builder()
                .updates(List.of(UpdateRoundStudentItem.builder()
                        .studentId(student1Id)
                        .status(RoundStudentStatus.SELECTED)
                        .build()))
                .build();

        assertThatThrownBy(() -> roundService.bulkUpdateRoundStudents(collegeId, round1Id, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must first be marked ATTENDED or ABSENT");
    }

    @Test
    @DisplayName("Transition validation: ABSENT student cannot be marked SELECTED")
    void testAbsentStudentCannotBeSelected() {
        when(driveRoundRepository.findById(round1Id)).thenReturn(Optional.of(driveRound1));
        when(driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)).thenReturn(Optional.of(drive));

        DriveRoundStudent drs = DriveRoundStudent.builder()
                .id(UUID.randomUUID())
                .driveRoundId(round1Id)
                .studentId(student1Id)
                .status(RoundStudentStatus.ABSENT)
                .build();

        when(driveRoundStudentRepository.findByDriveRoundIdAndStudentId(round1Id, student1Id))
                .thenReturn(Optional.of(drs));

        BulkUpdateRoundStudentsRequest request = BulkUpdateRoundStudentsRequest.builder()
                .updates(List.of(UpdateRoundStudentItem.builder()
                        .studentId(student1Id)
                        .status(RoundStudentStatus.SELECTED)
                        .build()))
                .build();

        assertThatThrownBy(() -> roundService.bulkUpdateRoundStudents(collegeId, round1Id, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("An ABSENT student cannot be selected");
    }

    @Test
    @DisplayName("Transition validation: ATTENDED student can be marked SELECTED or REJECTED")
    void testAttendedStudentCanBeSelected() {
        when(driveRoundRepository.findById(round1Id)).thenReturn(Optional.of(driveRound1));
        when(driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)).thenReturn(Optional.of(drive));

        DriveRoundStudent drs = DriveRoundStudent.builder()
                .id(UUID.randomUUID())
                .driveRoundId(round1Id)
                .studentId(student1Id)
                .status(RoundStudentStatus.ATTENDED)
                .build();

        when(driveRoundStudentRepository.findByDriveRoundIdAndStudentId(round1Id, student1Id))
                .thenReturn(Optional.of(drs));
        when(driveRoundStudentRepository.findByDriveRoundId(round1Id))
                .thenReturn(List.of(drs));
        when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(studentRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(branchRepository.findByCollegeId(collegeId)).thenReturn(Collections.emptyList());

        BulkUpdateRoundStudentsRequest request = BulkUpdateRoundStudentsRequest.builder()
                .updates(List.of(UpdateRoundStudentItem.builder()
                        .studentId(student1Id)
                        .status(RoundStudentStatus.SELECTED)
                        .score(new BigDecimal("92.50"))
                        .remarks("Excellent technical depth")
                        .build()))
                .build();

        var response = roundService.bulkUpdateRoundStudents(collegeId, round1Id, request);

        assertThat(response).isNotNull();
        assertThat(drs.getStatus()).isEqualTo(RoundStudentStatus.SELECTED);
        assertThat(drs.getScore()).isEqualTo(new BigDecimal("92.50"));
    }
}
