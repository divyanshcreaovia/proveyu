package com.proveyu.placement.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementDriveService {

    private final PlacementDriveRepository driveRepository;
    private final DriveEligibilityRepository eligibilityRepository;
    private final DriveTierRepository driveTierRepository;
    private final DriveBranchRepository driveBranchRepository;
    private final DriveRoleRepository driveRoleRepository;
    private final DriveRoundRepository driveRoundRepository;
    private final DriveRoundStudentRepository driveRoundStudentRepository;
    private final HiringRoundRepository hiringRoundRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final TierRepository tierRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PackageTierRuleService packageTierRuleService;
    private final PlacementTierService tierService;

    @Transactional
    public DriveResponse createDrive(UUID collegeId, CreateDriveRequest request) {
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new DomainException("Company not found with ID: " + request.getCompanyId(), HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND"));

        PlacementDrive drive = PlacementDrive.builder()
                .collegeId(collegeId)
                .companyId(company.getCompanyId())
                .offeredPackage(request.getOfferedPackage())
                .driveDate(request.getDriveDate())
                .location(request.getLocation())
                .placedPolicy(request.getPlacedPolicy() != null ? request.getPlacedPolicy() : PlacedPolicy.EXCLUDE_ALL)
                .status(DriveStatus.DRAFT)
                .build();

        PlacementDrive savedDrive = driveRepository.save(drive);

        // 1. Eligibility
        DriveEligibility eligibility = DriveEligibility.builder()
                .driveId(savedDrive.getDriveId())
                .build();
        if (request.getEligibility() != null) {
            eligibility.setMinCgpa(request.getEligibility().getMinCgpa());
            eligibility.setMinScore(request.getEligibility().getMinScore());
            eligibility.setMin10thPercent(request.getEligibility().getMin10thPercent());
            eligibility.setMin12thPercent(request.getEligibility().getMin12thPercent());
            eligibility.setMaxActiveBacklogs(request.getEligibility().getMaxActiveBacklogs());
            eligibility.setMaxGapYears(request.getEligibility().getMaxGapYears());
        }
        eligibilityRepository.save(eligibility);

        // 2. Drive Tiers (Admin manual override, or auto-filled from package rules)
        List<UUID> tierIds = request.getTierIds();
        if (tierIds == null || tierIds.isEmpty()) {
            tierIds = packageTierRuleService.getApplicableTierIds(collegeId, request.getOfferedPackage());
        }
        if (tierIds != null && !tierIds.isEmpty()) {
            for (UUID tierId : tierIds) {
                driveTierRepository.save(new DriveTier(new DriveTierId(savedDrive.getDriveId(), tierId)));
            }
        }

        // 3. Drive Branches (empty = all branches)
        if (request.getBranchIds() != null) {
            for (UUID branchId : request.getBranchIds()) {
                driveBranchRepository.save(new DriveBranch(new DriveBranchId(savedDrive.getDriveId(), branchId)));
            }
        }

        // 4. Drive Roles
        if (request.getRoles() != null) {
            for (String roleName : request.getRoles()) {
                driveRoleRepository.save(DriveRole.builder()
                        .driveId(savedDrive.getDriveId())
                        .roleName(roleName.trim())
                        .build());
            }
        }

        // 5. Initial Rounds
        if (request.getRoundIds() != null && !request.getRoundIds().isEmpty()) {
            int roundNumber = 1;
            for (UUID roundId : request.getRoundIds()) {
                driveRoundRepository.save(DriveRound.builder()
                        .driveId(savedDrive.getDriveId())
                        .roundId(roundId)
                        .roundNumber(roundNumber++)
                        .roundDate(savedDrive.getDriveDate())
                        .status(RoundStatus.PENDING)
                        .build());
            }
        } else {
            // Default: seed Round 1 (Online Assessment) if available
            hiringRoundRepository.findByRoundName("Online Assessment").ifPresent(hr -> {
                driveRoundRepository.save(DriveRound.builder()
                        .driveId(savedDrive.getDriveId())
                        .roundId(hr.getRoundId())
                        .roundNumber(1)
                        .roundDate(savedDrive.getDriveDate())
                        .status(RoundStatus.PENDING)
                        .build());
            });
        }

        return getDrive(collegeId, savedDrive.getDriveId());
    }

    @Transactional
    public DriveResponse updateDrive(UUID collegeId, UUID driveId, UpdateDriveRequest request) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        if (request.getOfferedPackage() != null) drive.setOfferedPackage(request.getOfferedPackage());
        if (request.getDriveDate() != null) drive.setDriveDate(request.getDriveDate());
        if (request.getLocation() != null) drive.setLocation(request.getLocation());
        if (request.getPlacedPolicy() != null) drive.setPlacedPolicy(request.getPlacedPolicy());
        if (request.getStatus() != null) drive.setStatus(request.getStatus());

        driveRepository.save(drive);

        if (request.getEligibility() != null) {
            DriveEligibility eligibility = eligibilityRepository.findByDriveId(driveId)
                    .orElse(DriveEligibility.builder().driveId(driveId).build());
            eligibility.setMinCgpa(request.getEligibility().getMinCgpa());
            eligibility.setMinScore(request.getEligibility().getMinScore());
            eligibility.setMin10thPercent(request.getEligibility().getMin10thPercent());
            eligibility.setMin12thPercent(request.getEligibility().getMin12thPercent());
            eligibility.setMaxActiveBacklogs(request.getEligibility().getMaxActiveBacklogs());
            eligibility.setMaxGapYears(request.getEligibility().getMaxGapYears());
            eligibilityRepository.save(eligibility);
        }

        if (request.getTierIds() != null) {
            driveTierRepository.deleteByIdDriveId(driveId);
            for (UUID tierId : request.getTierIds()) {
                driveTierRepository.save(new DriveTier(new DriveTierId(driveId, tierId)));
            }
        }

        if (request.getBranchIds() != null) {
            driveBranchRepository.deleteByIdDriveId(driveId);
            for (UUID branchId : request.getBranchIds()) {
                driveBranchRepository.save(new DriveBranch(new DriveBranchId(driveId, branchId)));
            }
        }

        if (request.getRoles() != null) {
            driveRoleRepository.deleteByDriveId(driveId);
            for (String roleName : request.getRoles()) {
                driveRoleRepository.save(DriveRole.builder()
                        .driveId(driveId)
                        .roleName(roleName.trim())
                        .build());
            }
        }

        return getDrive(collegeId, driveId);
    }

    @Transactional
    public void cancelDrive(UUID collegeId, UUID driveId) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));
        drive.setStatus(DriveStatus.CANCELLED);
        driveRepository.save(drive);
    }

    @Transactional(readOnly = true)
    public DriveResponse getDrive(UUID collegeId, UUID driveId) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        Company company = companyRepository.findById(drive.getCompanyId()).orElse(null);
        DriveEligibility eligibility = eligibilityRepository.findByDriveId(driveId).orElse(null);

        List<DriveTier> driveTiers = driveTierRepository.findByIdDriveId(driveId);
        List<UUID> tierIds = driveTiers.stream().map(dt -> dt.getId().getTierId()).collect(Collectors.toList());
        List<TierResponse> tiers = tierRepository.findAllById(tierIds).stream()
                .map(tierService::toResponse)
                .collect(Collectors.toList());

        List<DriveBranch> driveBranches = driveBranchRepository.findByIdDriveId(driveId);
        List<UUID> branchIds = driveBranches.stream().map(db -> db.getId().getBranchId()).collect(Collectors.toList());
        List<BranchResponse> branches = branchRepository.findAllById(branchIds).stream()
                .map(b -> BranchResponse.builder()
                        .branchId(b.getBranchId())
                        .collegeId(b.getCollegeId())
                        .branchName(b.getBranchName())
                        .branchCode(b.getBranchCode())
                        .createdAt(b.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<String> roles = driveRoleRepository.findByDriveId(driveId).stream()
                .map(DriveRole::getRoleName)
                .collect(Collectors.toList());

        DriveEligibilityDto eligDto = null;
        if (eligibility != null) {
            eligDto = DriveEligibilityDto.builder()
                    .minCgpa(eligibility.getMinCgpa())
                    .minScore(eligibility.getMinScore())
                    .min10thPercent(eligibility.getMin10thPercent())
                    .min12thPercent(eligibility.getMin12thPercent())
                    .maxActiveBacklogs(eligibility.getMaxActiveBacklogs())
                    .maxGapYears(eligibility.getMaxGapYears())
                    .build();
        }

        return DriveResponse.builder()
                .driveId(drive.getDriveId())
                .collegeId(drive.getCollegeId())
                .companyId(drive.getCompanyId())
                .companyName(company != null ? company.getName() : "Unknown")
                .offeredPackage(drive.getOfferedPackage())
                .driveDate(drive.getDriveDate())
                .location(drive.getLocation())
                .placedPolicy(drive.getPlacedPolicy())
                .status(drive.getStatus())
                .eligibility(eligDto)
                .tiers(tiers)
                .branches(branches)
                .roles(roles)
                .createdAt(drive.getCreatedAt())
                .updatedAt(drive.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DriveResponse> listDrives(UUID collegeId, DriveStatus status, LocalDate driveDate) {
        List<PlacementDrive> drives;
        if (status != null && driveDate != null) {
            drives = driveRepository.findByCollegeIdAndStatusAndDriveDate(collegeId, status, driveDate);
        } else if (status != null) {
            drives = driveRepository.findByCollegeIdAndStatus(collegeId, status);
        } else if (driveDate != null) {
            drives = driveRepository.findByCollegeIdAndDriveDate(collegeId, driveDate);
        } else {
            drives = driveRepository.findByCollegeId(collegeId);
        }

        return drives.stream()
                .map(d -> getDrive(collegeId, d.getDriveId()))
                .collect(Collectors.toList());
    }

    /**
     * Preview endpoint (writes nothing). Returns eligible students + total count.
     */
    @Transactional(readOnly = true)
    public DriveEligiblePreviewResponse previewEligibleStudents(UUID collegeId, UUID driveId) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        List<Student> eligibleStudents = evaluateEligibility(collegeId, drive);

        List<Branch> branches = branchRepository.findByCollegeId(collegeId);
        Map<UUID, String> branchNameMap = branches.stream()
                .collect(Collectors.toMap(Branch::getBranchId, Branch::getBranchName));

        List<UUID> userIds = eligibleStudents.stream().map(Student::getStudentId).collect(Collectors.toList());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<EligibleStudentResponse> responses = eligibleStudents.stream().map(student -> {
            User user = userMap.get(student.getStudentId());
            String branchName = branchNameMap.getOrDefault(student.getBranchId(), "Unknown Branch");
            Optional<Tier> tierOpt = tierService.deriveTier(collegeId, student.getScore());

            return EligibleStudentResponse.builder()
                    .studentId(student.getStudentId())
                    .fullName(user != null ? user.getFullName() : null)
                    .email(user != null ? user.getEmail() : null)
                    .phone(user != null ? user.getPhone() : null)
                    .rollNumber(student.getRollNumber())
                    .branchId(student.getBranchId())
                    .branchName(branchName)
                    .cgpa(student.getCgpa())
                    .score(student.getScore())
                    .tenthPercent(student.getTenthPercent())
                    .twelfthPercent(student.getTwelfthPercent())
                    .activeBacklogs(student.getActiveBacklogs())
                    .gapYears(student.getGapYears())
                    .tierName(tierOpt.map(Tier::getTierName).orElse(null))
                    .tierLevel(tierOpt.map(Tier::getTierLevel).orElse(null))
                    .placedStatus(student.getPlacedStatus())
                    .highestPackageLpa(student.getHighestPackageLpa())
                    .build();
        }).collect(Collectors.toList());

        return DriveEligiblePreviewResponse.builder()
                .totalCount(responses.size())
                .students(responses)
                .build();
    }

    /**
     * Finalize endpoint (freeze snapshot into Round 1 of drive_round_student as SHORTLISTED).
     * Idempotent: does not duplicate existing rows.
     */
    @Transactional
    public DriveEligiblePreviewResponse finalizeDrive(UUID collegeId, UUID driveId) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        // Find Round 1
        List<DriveRound> rounds = driveRoundRepository.findByDriveIdOrderByRoundNumberAsc(driveId);
        DriveRound round1;
        if (rounds.isEmpty()) {
            HiringRound defaultRound = hiringRoundRepository.findByRoundName("Online Assessment")
                    .orElseGet(() -> hiringRoundRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new DomainException("No hiring rounds configured in system", HttpStatus.INTERNAL_SERVER_ERROR, "NO_ROUNDS")));
            round1 = driveRoundRepository.save(DriveRound.builder()
                    .driveId(driveId)
                    .roundId(defaultRound.getRoundId())
                    .roundNumber(1)
                    .roundDate(drive.getDriveDate())
                    .status(RoundStatus.ONGOING)
                    .build());
        } else {
            round1 = rounds.get(0);
            if (round1.getStatus() == RoundStatus.PENDING) {
                round1.setStatus(RoundStatus.ONGOING);
                driveRoundRepository.save(round1);
            }
        }

        // Evaluate eligible students
        List<Student> eligibleStudents = evaluateEligibility(collegeId, drive);

        for (Student student : eligibleStudents) {
            if (!driveRoundStudentRepository.existsByDriveRoundIdAndStudentId(round1.getDriveRoundId(), student.getStudentId())) {
                driveRoundStudentRepository.save(DriveRoundStudent.builder()
                        .driveRoundId(round1.getDriveRoundId())
                        .studentId(student.getStudentId())
                        .status(RoundStudentStatus.SHORTLISTED)
                        .build());
            }
        }

        if (drive.getStatus() == DriveStatus.DRAFT) {
            drive.setStatus(DriveStatus.ONGOING);
            driveRepository.save(drive);
        }

        log.info("[DRIVE FINALIZED] Drive [{}] finalized with [{}] shortlisted students in Round 1 [{}]",
                driveId, eligibleStudents.size(), round1.getDriveRoundId());

        return previewEligibleStudents(collegeId, driveId);
    }

    /**
     * Core eligibility evaluation engine.
     * Evaluates all business rules:
     * 1. Student tier in drive_tier
     * 2. Branch in drive_branch (if any selected)
     * 3. Non-null criteria in drive_eligibility (cgpa, score, 10th%, 12th%, backlogs, gap years)
     * 4. placed_policy (EXCLUDE_ALL, INCLUDE_ALL, ONLY_IF_HIGHER_PACKAGE)
     */
    public List<Student> evaluateEligibility(UUID collegeId, PlacementDrive drive) {
        DriveEligibility eligibility = eligibilityRepository.findByDriveId(drive.getDriveId()).orElse(null);

        // Drive Tiers
        List<DriveTier> driveTiers = driveTierRepository.findByIdDriveId(drive.getDriveId());
        Set<UUID> allowedTierIds = driveTiers.stream()
                .map(dt -> dt.getId().getTierId())
                .collect(Collectors.toSet());

        // Drive Branches (empty = all)
        List<DriveBranch> driveBranches = driveBranchRepository.findByIdDriveId(drive.getDriveId());
        Set<UUID> allowedBranchIds = driveBranches.stream()
                .map(db -> db.getId().getBranchId())
                .collect(Collectors.toSet());

        List<Student> allCollegeStudents = studentRepository.findByCollegeId(collegeId);

        return allCollegeStudents.stream().filter(student -> {
            // 1. Branch check
            if (!allowedBranchIds.isEmpty() && !allowedBranchIds.contains(student.getBranchId())) {
                return false;
            }

            // 2. Tier check
            if (!allowedTierIds.isEmpty()) {
                Optional<Tier> derivedTier = tierService.deriveTier(collegeId, student.getScore());
                if (derivedTier.isEmpty() || !allowedTierIds.contains(derivedTier.get().getTierId())) {
                    return false;
                }
            }

            // 3. Drive eligibility criteria (non-null checks)
            if (eligibility != null) {
                if (eligibility.getMinCgpa() != null) {
                    if (student.getCgpa() == null || student.getCgpa().compareTo(eligibility.getMinCgpa()) < 0) {
                        return false;
                    }
                }
                if (eligibility.getMinScore() != null) {
                    if (student.getScore() == null || student.getScore().compareTo(eligibility.getMinScore()) < 0) {
                        return false;
                    }
                }
                if (eligibility.getMin10thPercent() != null) {
                    if (student.getTenthPercent() == null || student.getTenthPercent().compareTo(eligibility.getMin10thPercent()) < 0) {
                        return false;
                    }
                }
                if (eligibility.getMin12thPercent() != null) {
                    if (student.getTwelfthPercent() == null || student.getTwelfthPercent().compareTo(eligibility.getMin12thPercent()) < 0) {
                        return false;
                    }
                }
                if (eligibility.getMaxActiveBacklogs() != null) {
                    if (student.getActiveBacklogs() > eligibility.getMaxActiveBacklogs()) {
                        return false;
                    }
                }
                if (eligibility.getMaxGapYears() != null) {
                    if (student.getGapYears() > eligibility.getMaxGapYears()) {
                        return false;
                    }
                }
            }

            // 4. Placed Policy
            PlacedPolicy policy = drive.getPlacedPolicy() != null ? drive.getPlacedPolicy() : PlacedPolicy.EXCLUDE_ALL;
            if (policy == PlacedPolicy.EXCLUDE_ALL) {
                if (student.getPlacedStatus() == PlacedStatus.PLACED) {
                    return false;
                }
            } else if (policy == PlacedPolicy.ONLY_IF_HIGHER_PACKAGE) {
                if (student.getPlacedStatus() == PlacedStatus.PLACED) {
                    BigDecimal highestPackage = student.getHighestPackageLpa() != null ? student.getHighestPackageLpa() : BigDecimal.ZERO;
                    if (drive.getOfferedPackage().compareTo(highestPackage) <= 0) {
                        return false;
                    }
                }
            }
            // INCLUDE_ALL: no placed status restriction

            return true;
        }).collect(Collectors.toList());
    }
}
