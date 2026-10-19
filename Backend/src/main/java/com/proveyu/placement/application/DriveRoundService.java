package com.proveyu.placement.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveRoundService {

    private final DriveRoundRepository driveRoundRepository;
    private final DriveRoundStudentRepository driveRoundStudentRepository;
    private final PlacementDriveRepository driveRepository;
    private final HiringRoundRepository hiringRoundRepository;
    private final StudentRepository studentRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;

    @Transactional
    public DriveRoundResponse addRound(UUID collegeId, UUID driveId, CreateDriveRoundRequest request) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        HiringRound hiringRound = hiringRoundRepository.findById(request.getRoundId())
                .orElseThrow(() -> new DomainException("Hiring round not found", HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND"));

        driveRoundRepository.findByDriveIdAndRoundNumber(driveId, request.getRoundNumber())
                .ifPresent(existing -> {
                    throw new DomainException("Round number " + request.getRoundNumber() + " already exists for this drive", HttpStatus.CONFLICT, "ROUND_NUMBER_EXISTS");
                });

        DriveRound driveRound = DriveRound.builder()
                .driveId(drive.getDriveId())
                .roundId(hiringRound.getRoundId())
                .roundNumber(request.getRoundNumber())
                .roundDate(request.getRoundDate() != null ? request.getRoundDate() : drive.getDriveDate())
                .status(RoundStatus.PENDING)
                .build();

        DriveRound saved = driveRoundRepository.save(driveRound);
        return toRoundResponse(saved, hiringRound.getRoundName());
    }

    @Transactional(readOnly = true)
    public List<DriveRoundResponse> listRoundsForDrive(UUID collegeId, UUID driveId) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        List<DriveRound> rounds = driveRoundRepository.findByDriveIdOrderByRoundNumberAsc(drive.getDriveId());
        Map<UUID, String> roundNames = hiringRoundRepository.findAll().stream()
                .collect(Collectors.toMap(HiringRound::getRoundId, HiringRound::getRoundName));

        return rounds.stream()
                .map(r -> toRoundResponse(r, roundNames.getOrDefault(r.getRoundId(), "Round " + r.getRoundNumber())))
                .collect(Collectors.toList());
    }

    @Transactional
    public DriveRoundResponse completeRound(UUID collegeId, UUID driveRoundId) {
        DriveRound driveRound = driveRoundRepository.findById(driveRoundId)
                .orElseThrow(() -> new DomainException("Drive round not found", HttpStatus.NOT_FOUND, "DRIVE_ROUND_NOT_FOUND"));

        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveRound.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized drive access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_DRIVE"));

        driveRound.setStatus(RoundStatus.COMPLETED);
        DriveRound saved = driveRoundRepository.save(driveRound);

        String roundName = hiringRoundRepository.findById(saved.getRoundId())
                .map(HiringRound::getRoundName)
                .orElse("Round " + saved.getRoundNumber());

        return toRoundResponse(saved, roundName);
    }

    @Transactional(readOnly = true)
    public List<RoundStudentResponse> listStudentsInRound(UUID collegeId, UUID driveRoundId, RoundStudentStatus statusFilter) {
        DriveRound driveRound = driveRoundRepository.findById(driveRoundId)
                .orElseThrow(() -> new DomainException("Drive round not found", HttpStatus.NOT_FOUND, "DRIVE_ROUND_NOT_FOUND"));

        driveRepository.findByDriveIdAndCollegeId(driveRound.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized drive access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_DRIVE"));

        List<DriveRoundStudent> roundStudents;
        if (statusFilter != null) {
            roundStudents = driveRoundStudentRepository.findByDriveRoundIdAndStatus(driveRoundId, statusFilter);
        } else {
            roundStudents = driveRoundStudentRepository.findByDriveRoundId(driveRoundId);
        }

        List<UUID> studentIds = roundStudents.stream().map(DriveRoundStudent::getStudentId).collect(Collectors.toList());
        Map<UUID, User> userMap = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Student> studentMap = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getStudentId, s -> s));

        List<Branch> branches = branchRepository.findByCollegeId(collegeId);
        Map<UUID, String> branchNames = branches.stream()
                .collect(Collectors.toMap(Branch::getBranchId, Branch::getBranchName));

        return roundStudents.stream().map(drs -> {
            User user = userMap.get(drs.getStudentId());
            Student student = studentMap.get(drs.getStudentId());
            String branchName = (student != null && branchNames.containsKey(student.getBranchId()))
                    ? branchNames.get(student.getBranchId()) : "Unknown Branch";

            return RoundStudentResponse.builder()
                    .id(drs.getId())
                    .driveRoundId(drs.getDriveRoundId())
                    .studentId(drs.getStudentId())
                    .fullName(user != null ? user.getFullName() : null)
                    .email(user != null ? user.getEmail() : null)
                    .rollNumber(student != null ? student.getRollNumber() : null)
                    .branchName(branchName)
                    .status(drs.getStatus())
                    .score(drs.getScore())
                    .remarks(drs.getRemarks())
                    .mailSentAt(drs.getMailSentAt())
                    .updatedAt(drs.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Bulk update status / score of students in a round.
     * Enforces business rule transitions:
     * SHORTLISTED -> (ABSENT | ATTENDED) -> (SELECTED | REJECTED).
     * Blocks invalid transitions. Absent students cannot be selected.
     */
    @Transactional
    public List<RoundStudentResponse> bulkUpdateRoundStudents(UUID collegeId, UUID driveRoundId, BulkUpdateRoundStudentsRequest request) {
        DriveRound driveRound = driveRoundRepository.findById(driveRoundId)
                .orElseThrow(() -> new DomainException("Drive round not found", HttpStatus.NOT_FOUND, "DRIVE_ROUND_NOT_FOUND"));

        driveRepository.findByDriveIdAndCollegeId(driveRound.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized drive access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_DRIVE"));

        for (UpdateRoundStudentItem item : request.getUpdates()) {
            DriveRoundStudent drs = driveRoundStudentRepository.findByDriveRoundIdAndStudentId(driveRoundId, item.getStudentId())
                    .orElseThrow(() -> new DomainException("Student " + item.getStudentId() + " not in this drive round", HttpStatus.NOT_FOUND, "STUDENT_NOT_IN_ROUND"));

            validateStatusTransition(drs.getStatus(), item.getStatus());

            drs.setStatus(item.getStatus());
            if (item.getScore() != null) {
                drs.setScore(item.getScore());
            }
            if (item.getRemarks() != null) {
                drs.setRemarks(item.getRemarks());
            }
            driveRoundStudentRepository.save(drs);
        }

        return listStudentsInRound(collegeId, driveRoundId, null);
    }

    /**
     * Advance round:
     * Moves students with status SELECTED from the current round into the next round (by round_number) as SHORTLISTED.
     * Idempotent (does not re-insert already shortlisted students).
     */
    @Transactional
    public DriveRoundResponse advanceRound(UUID collegeId, UUID driveRoundId) {
        DriveRound currentRound = driveRoundRepository.findById(driveRoundId)
                .orElseThrow(() -> new DomainException("Drive round not found", HttpStatus.NOT_FOUND, "DRIVE_ROUND_NOT_FOUND"));

        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(currentRound.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized drive access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_DRIVE"));

        // Find next round by round_number
        List<DriveRound> allRounds = driveRoundRepository.findByDriveIdOrderByRoundNumberAsc(drive.getDriveId());
        DriveRound nextRound = allRounds.stream()
                .filter(r -> r.getRoundNumber() > currentRound.getRoundNumber())
                .findFirst()
                .orElseThrow(() -> new DomainException("No next round found. Current round is the final round.", HttpStatus.BAD_REQUEST, "NO_NEXT_ROUND"));

        // Students with SELECTED in current round
        List<DriveRoundStudent> selectedStudents = driveRoundStudentRepository.findByDriveRoundIdAndStatus(driveRoundId, RoundStudentStatus.SELECTED);
        if (selectedStudents.isEmpty()) {
            throw new DomainException("No students with status SELECTED to advance to the next round", HttpStatus.BAD_REQUEST, "NO_SELECTED_STUDENTS");
        }

        for (DriveRoundStudent drs : selectedStudents) {
            if (!driveRoundStudentRepository.existsByDriveRoundIdAndStudentId(nextRound.getDriveRoundId(), drs.getStudentId())) {
                driveRoundStudentRepository.save(DriveRoundStudent.builder()
                        .driveRoundId(nextRound.getDriveRoundId())
                        .studentId(drs.getStudentId())
                        .status(RoundStudentStatus.SHORTLISTED)
                        .build());
            }
        }

        // Update round statuses
        currentRound.setStatus(RoundStatus.COMPLETED);
        driveRoundRepository.save(currentRound);

        if (nextRound.getStatus() == RoundStatus.PENDING) {
            nextRound.setStatus(RoundStatus.ONGOING);
            driveRoundRepository.save(nextRound);
        }

        log.info("[ROUND ADVANCED] Advanced [{}] students from Round [{}] to Round [{}] in Drive [{}]",
                selectedStudents.size(), currentRound.getRoundNumber(), nextRound.getRoundNumber(), drive.getDriveId());

        String nextRoundName = hiringRoundRepository.findById(nextRound.getRoundId())
                .map(HiringRound::getRoundName)
                .orElse("Round " + nextRound.getRoundNumber());

        return toRoundResponse(nextRound, nextRoundName);
    }

    /**
     * Send email notifications to SHORTLISTED students in a round whose mail_sent_at IS NULL.
     */
    @Transactional
    public Map<String, Object> sendShortlistEmails(UUID collegeId, UUID driveRoundId) {
        DriveRound driveRound = driveRoundRepository.findById(driveRoundId)
                .orElseThrow(() -> new DomainException("Drive round not found", HttpStatus.NOT_FOUND, "DRIVE_ROUND_NOT_FOUND"));

        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveRound.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized drive access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_DRIVE"));

        Company company = companyRepository.findById(drive.getCompanyId()).orElse(null);
        String companyName = company != null ? company.getName() : "Recruiter";

        String roundName = hiringRoundRepository.findById(driveRound.getRoundId())
                .map(HiringRound::getRoundName)
                .orElse("Round " + driveRound.getRoundNumber());

        List<DriveRoundStudent> pendingStudents = driveRoundStudentRepository
                .findByDriveRoundIdAndStatusAndMailSentAtIsNull(driveRoundId, RoundStudentStatus.SHORTLISTED);

        int sentCount = 0;
        Instant now = Instant.now();

        for (DriveRoundStudent drs : pendingStudents) {
            User user = userRepository.findById(drs.getStudentId()).orElse(null);
            if (user != null && user.getEmail() != null) {
                emailService.sendPlacementDriveShortlistEmail(
                        user.getEmail(),
                        user.getFullName(),
                        companyName,
                        roundName,
                        drive.getOfferedPackage().toString(),
                        drive.getDriveDate() != null ? drive.getDriveDate().toString() : null
                );
                drs.setMailSentAt(now);
                driveRoundStudentRepository.save(drs);
                sentCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Shortlist emails dispatched successfully");
        result.put("dispatchedCount", sentCount);
        return result;
    }

    /**
     * Validates round transitions:
     * Allowed transitions:
     * - SHORTLISTED -> ABSENT
     * - SHORTLISTED -> ATTENDED
     * - ATTENDED -> SELECTED
     * - ATTENDED -> REJECTED
     * - Selected/Rejected cannot be set if status was ABSENT.
     * - Same status is allowed (idempotent update).
     */
    private void validateStatusTransition(RoundStudentStatus current, RoundStudentStatus target) {
        if (current == target) {
            return;
        }

        if (current == RoundStudentStatus.SHORTLISTED) {
            if (target != RoundStudentStatus.ABSENT && target != RoundStudentStatus.ATTENDED) {
                throw new DomainException("Invalid status transition: A SHORTLISTED student must first be marked ATTENDED or ABSENT before final evaluation",
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
            }
        } else if (current == RoundStudentStatus.ABSENT) {
            throw new DomainException("Invalid status transition: An ABSENT student cannot be selected or attended",
                    HttpStatus.BAD_REQUEST, "ABSENT_CANNOT_BE_SELECTED");
        } else if (current == RoundStudentStatus.ATTENDED) {
            if (target != RoundStudentStatus.SELECTED && target != RoundStudentStatus.REJECTED) {
                throw new DomainException("Invalid status transition: An ATTENDED student can only be marked SELECTED or REJECTED",
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
            }
        } else if (current == RoundStudentStatus.SELECTED || current == RoundStudentStatus.REJECTED) {
            // Allow changing between SELECTED and REJECTED if corrections are needed before advance
            if (target != RoundStudentStatus.SELECTED && target != RoundStudentStatus.REJECTED) {
                throw new DomainException("Invalid status transition from finalized status: " + current,
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
            }
        }
    }

    private DriveRoundResponse toRoundResponse(DriveRound round, String roundName) {
        long shortlisted = driveRoundStudentRepository.countByDriveRoundIdAndStatus(round.getDriveRoundId(), RoundStudentStatus.SHORTLISTED);
        long attended = driveRoundStudentRepository.countByDriveRoundIdAndStatus(round.getDriveRoundId(), RoundStudentStatus.ATTENDED);
        long selected = driveRoundStudentRepository.countByDriveRoundIdAndStatus(round.getDriveRoundId(), RoundStudentStatus.SELECTED);
        long rejected = driveRoundStudentRepository.countByDriveRoundIdAndStatus(round.getDriveRoundId(), RoundStudentStatus.REJECTED);
        long absent = driveRoundStudentRepository.countByDriveRoundIdAndStatus(round.getDriveRoundId(), RoundStudentStatus.ABSENT);

        return DriveRoundResponse.builder()
                .driveRoundId(round.getDriveRoundId())
                .driveId(round.getDriveId())
                .roundId(round.getRoundId())
                .roundName(roundName)
                .roundNumber(round.getRoundNumber())
                .roundDate(round.getRoundDate())
                .status(round.getStatus())
                .shortlistedCount(shortlisted)
                .attendedCount(attended)
                .selectedCount(selected)
                .rejectedCount(rejected)
                .absentCount(absent)
                .createdAt(round.getCreatedAt())
                .build();
    }
}
