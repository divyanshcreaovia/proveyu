package com.proveyu.placement.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.domain.OrganizationCandidate;
import com.proveyu.organization.infrastructure.OrganizationCandidateRepository;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.placement.application.dto.*;
import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import com.proveyu.shared.error.DomainException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@Service
public class PlacementManagementService {

    private static final Logger log = LoggerFactory.getLogger(PlacementManagementService.class);

    private final StudentProfileRepository studentProfileRepository;
    private final CollegeTeamMemberRepository teamMemberRepository;
    private final CollegeCustomRoleRepository customRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationCandidateRepository organizationCandidateRepository;
    private final UserRepository userRepository;
    private final PlacementRecordRepository placementRecordRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final ExcelStudentParser excelStudentParser;
    private final StudentTemplateGenerator templateGenerator;
    private final PasswordEncoder passwordEncoder;

    public PlacementManagementService(StudentProfileRepository studentProfileRepository,
                                      CollegeTeamMemberRepository teamMemberRepository,
                                      CollegeCustomRoleRepository customRoleRepository,
                                      OrganizationRepository organizationRepository,
                                      OrganizationCandidateRepository organizationCandidateRepository,
                                      UserRepository userRepository,
                                      PlacementRecordRepository placementRecordRepository,
                                      PlacementDriveRepository placementDriveRepository,
                                      ExcelStudentParser excelStudentParser,
                                      StudentTemplateGenerator templateGenerator,
                                      PasswordEncoder passwordEncoder) {
        this.studentProfileRepository = studentProfileRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.customRoleRepository = customRoleRepository;
        this.organizationRepository = organizationRepository;
        this.organizationCandidateRepository = organizationCandidateRepository;
        this.userRepository = userRepository;
        this.placementRecordRepository = placementRecordRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.excelStudentParser = excelStudentParser;
        this.templateGenerator = templateGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    public static class OrgUserContext {
        public UUID organizationId;
        public String roleName;
        public UUID customRoleId;
        public String assignedBranch; // "ALL" or specific branch name
        public boolean isHeadTpo; // Full administrative placement authority
        public boolean canViewStudents = true;
        public boolean canEditStudents = true;
        public boolean canManagePlacements = true;
        public boolean canInviteTeam = true;
        public boolean canExportReports = true;

        public boolean canAccessBranch(String branch) {
            if (isHeadTpo || "ALL".equalsIgnoreCase(assignedBranch)) {
                return true;
            }
            return assignedBranch != null && assignedBranch.equalsIgnoreCase(branch);
        }
    }

    public OrgUserContext resolveContext(UUID userId) {
        OrgUserContext ctx = new OrgUserContext();

        // 1. Check if user is direct owner of Organization
        Optional<Organization> directOrg = organizationRepository.findByUserId(userId);
        if (directOrg.isPresent()) {
            ctx.organizationId = directOrg.get().getId();
            ctx.roleName = "Institution Admin";
            ctx.assignedBranch = "ALL";
            ctx.isHeadTpo = true;
            ctx.canViewStudents = true;
            ctx.canEditStudents = true;
            ctx.canManagePlacements = true;
            ctx.canInviteTeam = true;
            ctx.canExportReports = true;
            return ctx;
        }

        // 2. Check if user is a member of college_team_members
        Optional<CollegeTeamMember> member = teamMemberRepository.findByUserId(userId);
        if (member.isPresent()) {
            CollegeTeamMember m = member.get();
            ctx.organizationId = m.getOrganizationId();
            ctx.roleName = m.getRoleName();
            ctx.customRoleId = m.getCustomRoleId();
            ctx.assignedBranch = m.getAssignedBranch() != null ? m.getAssignedBranch() : "ALL";

            if (m.getCustomRoleId() != null) {
                Optional<CollegeCustomRole> crOpt = customRoleRepository.findById(m.getCustomRoleId());
                if (crOpt.isPresent()) {
                    CollegeCustomRole cr = crOpt.get();
                    ctx.canViewStudents = cr.isCanViewStudents();
                    ctx.canEditStudents = cr.isCanEditStudents();
                    ctx.canManagePlacements = cr.isCanManagePlacements();
                    ctx.canInviteTeam = cr.isCanInviteTeam();
                    ctx.canExportReports = cr.isCanExportReports();
                    ctx.isHeadTpo = cr.isCanInviteTeam() && "ALL".equalsIgnoreCase(cr.getAssignedBranch());
                    return ctx;
                }
            }

            ctx.canViewStudents = true;
            ctx.canEditStudents = true;
            ctx.canManagePlacements = false;
            ctx.canInviteTeam = false;
            ctx.canExportReports = true;
            ctx.isHeadTpo = "Institution Admin".equalsIgnoreCase(m.getRoleName()) || "ALL".equalsIgnoreCase(m.getAssignedBranch());
            return ctx;
        }

        // 3. Fallback: Check if there is any organization registered (for local development or single college setup)
        List<Organization> allOrgs = organizationRepository.findAll();
        if (!allOrgs.isEmpty()) {
            ctx.organizationId = allOrgs.get(0).getId();
            ctx.roleName = "Institution Admin";
            ctx.assignedBranch = "ALL";
            ctx.isHeadTpo = true;
            return ctx;
        }

        throw new DomainException("College or organization profile not found for user", HttpStatus.FORBIDDEN, "ORG_NOT_FOUND");
    }

    @Transactional
    public BulkUploadResultDto bulkUploadStudents(UUID currentUserId, MultipartFile file) {
        OrgUserContext ctx = resolveContext(currentUserId);
        List<ExcelStudentParser.ParsedStudentRow> parsedRows = excelStudentParser.parse(file);

        int importedCount = 0;
        int updatedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        List<StudentProfileDto> importedStudents = new ArrayList<>();

        for (ExcelStudentParser.ParsedStudentRow row : parsedRows) {
            if (!row.isValid()) {
                failedCount++;
                errors.add("Row " + row.rowNumber + " (" + row.rollNumber + "): " + String.join(", ", row.errors));
                continue;
            }

            // If non-TPO is uploading, ensure branch matches their scope
            if (!ctx.canAccessBranch(row.branch)) {
                failedCount++;
                errors.add("Row " + row.rowNumber + ": You do not have permission to onboard students to branch '" + row.branch + "'");
                continue;
            }

            try {
                // Find or create User
                User candidateUser = userRepository.findByEmail(row.email).orElseGet(() -> {
                    String defaultPassword = "Student@" + row.rollNumber;
                    User newUser = User.builder()
                            .email(row.email)
                            .fullName(row.firstName + (row.lastName != null ? " " + row.lastName : ""))
                            .phone(row.phone)
                            .passwordHash(passwordEncoder.encode(defaultPassword))
                            .role(Role.CANDIDATE)
                            .active(true)
                            .build();
                    return userRepository.save(newUser);
                });

                // Find or create StudentProfile
                Optional<StudentProfile> existingOpt = studentProfileRepository
                        .findByOrganizationIdAndRollNumber(ctx.organizationId, row.rollNumber);

                StudentProfile profile = existingOpt.orElseGet(StudentProfile::new);
                boolean isNew = profile.getId() == null;

                profile.setOrganizationId(ctx.organizationId);
                profile.setUserId(candidateUser.getId());
                profile.setRollNumber(row.rollNumber);
                profile.setFirstName(row.firstName);
                profile.setLastName(row.lastName != null ? row.lastName : "");
                profile.setEmail(row.email);
                profile.setPhone(row.phone);
                profile.setDob(row.dob);
                profile.setGender(row.gender);
                profile.setAadharNumber(row.aadharNumber);
                profile.setPanNumber(row.panNumber);
                profile.setResidentialAddress(row.residentialAddress);
                profile.setCollegeName(row.collegeName);
                profile.setBatchYear(row.batchYear);
                profile.setBranch(row.branch);
                profile.setDegree(row.degree);

                profile.setTenthPercentage(row.tenthPercentage);
                profile.setTwelfthPercentage(row.twelfthPercentage);
                profile.setActiveBacklogs(row.activeBacklogs);
                profile.setGapYears(row.gapYears);
                profile.setGradDate(row.gradDate);

                profile.setSem1Sgpa(row.sem1Sgpa);
                profile.setSem2Sgpa(row.sem2Sgpa);
                profile.setSem3Sgpa(row.sem3Sgpa);
                profile.setSem4Sgpa(row.sem4Sgpa);
                profile.setSem5Sgpa(row.sem5Sgpa);
                profile.setSem6Sgpa(row.sem6Sgpa);
                profile.setSem7Sgpa(row.sem7Sgpa);
                profile.setSem8Sgpa(row.sem8Sgpa);

                if (row.aggregateCgpa != null && row.aggregateCgpa > 0) {
                    profile.setAggregateCgpa(row.aggregateCgpa);
                } else {
                    profile.recalculateCgpa();
                }

                if (row.placementStatus != null && !row.placementStatus.trim().isEmpty()) {
                    profile.setPlacementStatus(row.placementStatus);
                }
                if (row.placedCompany != null) profile.setPlacedCompany(row.placedCompany);
                if (row.placedRole != null) profile.setPlacedRole(row.placedRole);
                if (row.placedCtc != null) profile.setPlacedCtc(row.placedCtc);

                profile.setLinkedinUrl(row.linkedinUrl);
                profile.setGithubUrl(row.githubUrl);
                profile.setPortfolioUrl(row.portfolioUrl);

                StudentProfile savedProfile = studentProfileRepository.save(profile);

                // Ensure organization candidate binding
                if (!organizationCandidateRepository.existsByOrganizationIdAndCandidateId(ctx.organizationId, candidateUser.getId())) {
                    OrganizationCandidate mapping = OrganizationCandidate.builder()
                            .organizationId(ctx.organizationId)
                            .candidateId(candidateUser.getId())
                            .rollNumberOrId(row.rollNumber)
                            .batchYear(row.batchYear)
                            .build();
                    organizationCandidateRepository.save(mapping);
                }

                if (isNew) {
                    importedCount++;
                } else {
                    updatedCount++;
                }
                importedStudents.add(StudentProfileDto.fromEntity(savedProfile));

            } catch (Exception ex) {
                log.error("Error processing row {}: {}", row.rowNumber, ex.getMessage(), ex);
                failedCount++;
                errors.add("Row " + row.rowNumber + " (" + row.rollNumber + "): " + ex.getMessage());
            }
        }

        return BulkUploadResultDto.builder()
                .totalRows(parsedRows.size())
                .importedCount(importedCount)
                .updatedCount(updatedCount)
                .failedCount(failedCount)
                .errors(errors)
                .importedStudents(importedStudents)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<StudentProfileDto> getStudents(UUID currentUserId,
                                               String search,
                                               String branch,
                                               String tier,
                                               String status,
                                               Double minCgpa,
                                               Integer maxBacklogs,
                                               Integer maxGapYears,
                                               Double minTenth,
                                               Double minTwelfth,
                                               Pageable pageable) {
        OrgUserContext ctx = resolveContext(currentUserId);

        // Branch-scoped role enforcement:
        // If logged-in user is HOD or Teacher, force branch to their assigned department
        String effectiveBranch = branch;
        if (!ctx.isHeadTpo && !"ALL".equalsIgnoreCase(ctx.assignedBranch)) {
            effectiveBranch = ctx.assignedBranch;
        }

        final String finalBranch = effectiveBranch;

        Specification<StudentProfile> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), ctx.organizationId));

            if (finalBranch != null && !finalBranch.equalsIgnoreCase("All") && !finalBranch.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("branch"), finalBranch));
            }

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("rollNumber")), pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("branch")), pattern)
                );
                predicates.add(searchPredicate);
            }

            if (tier != null && !tier.equalsIgnoreCase("All") && !tier.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("tier"), tier));
            }

            if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("placementStatus"), status));
            }

            if (minCgpa != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("aggregateCgpa"), minCgpa));
            }

            if (maxBacklogs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("activeBacklogs"), maxBacklogs));
            }

            if (maxGapYears != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("gapYears"), maxGapYears));
            }

            if (minTenth != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("tenthPercentage"), minTenth));
            }

            if (minTwelfth != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("twelfthPercentage"), minTwelfth));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return studentProfileRepository.findAll(spec, pageable).map(StudentProfileDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public StudentProfileDto getStudentById(UUID currentUserId, UUID studentProfileId) {
        OrgUserContext ctx = resolveContext(currentUserId);
        StudentProfile profile = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new DomainException("Student not found", HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND"));

        if (!profile.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Student does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (!ctx.canAccessBranch(profile.getBranch())) {
            throw new DomainException("You do not have permission to view students from " + profile.getBranch(),
                    HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED");
        }

        return StudentProfileDto.fromEntity(profile);
    }

    @Transactional
    public StudentProfileDto createStudent(UUID currentUserId, StudentProfileDto dto) {
        OrgUserContext ctx = resolveContext(currentUserId);

        if (!ctx.canAccessBranch(dto.getBranch())) {
            throw new DomainException("You do not have permission to create students in branch " + dto.getBranch(),
                    HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED");
        }

        studentProfileRepository.findByOrganizationIdAndRollNumber(ctx.organizationId, dto.getRollNumber())
                .ifPresent(existing -> {
                    throw new DomainException("Student with roll number " + dto.getRollNumber() + " already exists",
                            HttpStatus.BAD_REQUEST, "ROLL_NUMBER_EXISTS");
                });

        // Create or get user
        User candidate = userRepository.findByEmail(dto.getEmail()).orElseGet(() -> {
            String defaultPassword = "Student@" + dto.getRollNumber();
            User user = User.builder()
                    .email(dto.getEmail())
                    .fullName(dto.getFirstName() + " " + (dto.getLastName() != null ? dto.getLastName() : ""))
                    .phone(dto.getPhone())
                    .passwordHash(passwordEncoder.encode(defaultPassword))
                    .role(Role.CANDIDATE)
                    .active(true)
                    .build();
            return userRepository.save(user);
        });

        StudentProfile profile = new StudentProfile();
        copyDtoToProfile(dto, profile);
        profile.setOrganizationId(ctx.organizationId);
        profile.setUserId(candidate.getId());
        profile.recalculateCgpa();

        StudentProfile saved = studentProfileRepository.save(profile);

        if (!organizationCandidateRepository.existsByOrganizationIdAndCandidateId(ctx.organizationId, candidate.getId())) {
            OrganizationCandidate oc = OrganizationCandidate.builder()
                    .organizationId(ctx.organizationId)
                    .candidateId(candidate.getId())
                    .rollNumberOrId(dto.getRollNumber())
                    .batchYear(dto.getBatchYear())
                    .build();
            organizationCandidateRepository.save(oc);
        }

        return StudentProfileDto.fromEntity(saved);
    }

    @Transactional
    public StudentProfileDto updateStudent(UUID currentUserId, UUID studentProfileId, StudentProfileDto dto) {
        OrgUserContext ctx = resolveContext(currentUserId);
        StudentProfile profile = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new DomainException("Student not found", HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND"));

        if (!profile.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Student does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (!ctx.canAccessBranch(profile.getBranch())) {
            throw new DomainException("You do not have permission to update students in " + profile.getBranch(),
                    HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED");
        }

        String oldStatus = profile.getPlacementStatus();
        copyDtoToProfile(dto, profile);
        profile.recalculateCgpa();

        StudentProfile saved = studentProfileRepository.save(profile);

        // Record placement in audit tracker if student was transitioned to Placed
        if ("Placed".equalsIgnoreCase(profile.getPlacementStatus()) &&
                (profile.getPlacedCompany() != null && !profile.getPlacedCompany().trim().isEmpty())) {
            PlacementRecord record = new PlacementRecord();
            record.setOrganizationId(ctx.organizationId);
            record.setStudentProfileId(saved.getId());
            record.setCompanyName(profile.getPlacedCompany());
            record.setRoleOffered(profile.getPlacedRole() != null ? profile.getPlacedRole() : "Graduate Trainee");
            record.setCtc(profile.getPlacedCtc() != null ? profile.getPlacedCtc() : "Standard Package");
            record.setPackageCategory(profile.getPackageCategory());
            record.setBondDuration(profile.getBondDuration());
            record.setOfferDate(profile.getOfferDate() != null ? profile.getOfferDate() : LocalDate.now());
            record.setRecruitmentSource(profile.getRecruitmentSource() != null ? profile.getRecruitmentSource() : "Campus Drive");
            record.setStatus("OFFERED");
            placementRecordRepository.save(record);
        }

        return StudentProfileDto.fromEntity(saved);
    }

    @Transactional
    public void bulkUpdatePlacement(UUID currentUserId, BulkPlacementRequestDto dto) {
        OrgUserContext ctx = resolveContext(currentUserId);

        for (UUID studentId : dto.getStudentIds()) {
            Optional<StudentProfile> opt = studentProfileRepository.findById(studentId);
            if (opt.isPresent()) {
                StudentProfile profile = opt.get();
                if (profile.getOrganizationId().equals(ctx.organizationId)) {
                    if (ctx.canAccessBranch(profile.getBranch())) {
                        profile.setPlacementStatus("Placed");
                        profile.setPlacedCompany(dto.getCompany());
                        profile.setPlacedRole(dto.getRole());
                        profile.setPlacedCtc(dto.getCtc());
                        profile.setPackageCategory(dto.getPackageCategory());
                        profile.setBondDuration(dto.getBondDuration());
                        profile.setOfferDate(dto.getOfferDate() != null ? dto.getOfferDate() : LocalDate.now());
                        profile.setJoiningDate(dto.getJoiningDate());
                        profile.setRecruitmentSource(dto.getRecruitmentSource() != null ? dto.getRecruitmentSource() : "Campus Placement Drive");
                        studentProfileRepository.save(profile);

                        PlacementRecord record = new PlacementRecord();
                        record.setOrganizationId(ctx.organizationId);
                        record.setStudentProfileId(profile.getId());
                        record.setCompanyName(dto.getCompany());
                        record.setRoleOffered(dto.getRole());
                        record.setCtc(dto.getCtc());
                        record.setPackageCategory(dto.getPackageCategory());
                        record.setBondDuration(dto.getBondDuration());
                        record.setOfferDate(dto.getOfferDate() != null ? dto.getOfferDate() : LocalDate.now());
                        record.setJoiningDate(dto.getJoiningDate());
                        record.setRecruitmentSource(dto.getRecruitmentSource());
                        record.setStatus("OFFERED");
                        placementRecordRepository.save(record);
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public StudentProfileDto getStudentSelfProfile(UUID candidateUserId) {
        StudentProfile profile = studentProfileRepository.findByUserId(candidateUserId)
                .orElseThrow(() -> new DomainException("Student profile not found", HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND"));
        return StudentProfileDto.fromEntity(profile);
    }

    @Transactional
    public StudentProfileDto updateStudentSelfProfile(UUID candidateUserId, StudentProfileDto dto) {
        StudentProfile profile = studentProfileRepository.findByUserId(candidateUserId)
                .orElseThrow(() -> new DomainException("Student profile not found", HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND"));

        // Students can update their personal information, social links, and portfolio
        if (dto.getPhone() != null) profile.setPhone(dto.getPhone());
        if (dto.getResidentialAddress() != null) profile.setResidentialAddress(dto.getResidentialAddress());
        if (dto.getLinkedinUrl() != null) profile.setLinkedinUrl(dto.getLinkedinUrl());
        if (dto.getGithubUrl() != null) profile.setGithubUrl(dto.getGithubUrl());
        if (dto.getPortfolioUrl() != null) profile.setPortfolioUrl(dto.getPortfolioUrl());
        if (dto.getDob() != null) profile.setDob(dto.getDob());
        if (dto.getGender() != null) profile.setGender(dto.getGender());

        StudentProfile saved = studentProfileRepository.save(profile);
        return StudentProfileDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CollegeDashboardDto getDashboardKpi(UUID currentUserId) {
        OrgUserContext ctx = resolveContext(currentUserId);
        long totalStudents = studentProfileRepository.countByOrganizationId(ctx.organizationId);
        long placedStudents = studentProfileRepository.countByOrganizationIdAndPlacementStatus(ctx.organizationId, "Placed");
        long unplacedStudents = totalStudents - placedStudents;
        double placementPercentage = totalStudents > 0 ? Math.round(((double) placedStudents / totalStudents * 100) * 10.0) / 10.0 : 0.0;
        long activeDrives = placementDriveRepository.countByOrganizationId(ctx.organizationId);

        List<StudentProfile> all = studentProfileRepository.findByOrganizationId(ctx.organizationId);
        Map<String, Long> branchStats = new HashMap<>();
        for (StudentProfile s : all) {
            if ("Placed".equalsIgnoreCase(s.getPlacementStatus())) {
                branchStats.put(s.getBranch(), branchStats.getOrDefault(s.getBranch(), 0L) + 1);
            }
        }

        List<PlacementDrive> drives = placementDriveRepository.findByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId);
        List<PlacementDriveDto.Response> driveResponses = drives.stream().limit(5).map(PlacementDriveDto.Response::fromEntity).toList();

        return CollegeDashboardDto.builder()
                .totalStudents(totalStudents)
                .placedStudents(placedStudents)
                .unplacedStudents(unplacedStudents)
                .placementPercentage(placementPercentage)
                .activeDrives(activeDrives)
                .averagePackageLpa(6.4)
                .highestPackageLpa(18.5)
                .branchWisePlacements(branchStats)
                .upcomingDrives(driveResponses)
                .build();
    }

    public byte[] downloadTemplate(boolean withSampleData) {
        return templateGenerator.generateCsvTemplate(withSampleData);
    }

    private void copyDtoToProfile(StudentProfileDto dto, StudentProfile profile) {
        if (dto.getRollNumber() != null) profile.setRollNumber(dto.getRollNumber());
        if (dto.getFirstName() != null) profile.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) profile.setLastName(dto.getLastName());
        if (dto.getEmail() != null) profile.setEmail(dto.getEmail());
        if (dto.getPhone() != null) profile.setPhone(dto.getPhone());
        if (dto.getDob() != null) profile.setDob(dto.getDob());
        if (dto.getGender() != null) profile.setGender(dto.getGender());
        if (dto.getAadharNumber() != null) profile.setAadharNumber(dto.getAadharNumber());
        if (dto.getPanNumber() != null) profile.setPanNumber(dto.getPanNumber());
        if (dto.getResidentialAddress() != null) profile.setResidentialAddress(dto.getResidentialAddress());
        if (dto.getCollegeName() != null) profile.setCollegeName(dto.getCollegeName());
        if (dto.getBatchYear() != null) profile.setBatchYear(dto.getBatchYear());
        if (dto.getBranch() != null) profile.setBranch(dto.getBranch());
        if (dto.getDegree() != null) profile.setDegree(dto.getDegree());

        if (dto.getTenthPercentage() != null) profile.setTenthPercentage(dto.getTenthPercentage());
        if (dto.getTwelfthPercentage() != null) profile.setTwelfthPercentage(dto.getTwelfthPercentage());
        if (dto.getActiveBacklogs() != null) profile.setActiveBacklogs(dto.getActiveBacklogs());
        if (dto.getGapYears() != null) profile.setGapYears(dto.getGapYears());
        if (dto.getGradDate() != null) profile.setGradDate(dto.getGradDate());

        if (dto.getSem1Sgpa() != null) profile.setSem1Sgpa(dto.getSem1Sgpa());
        if (dto.getSem2Sgpa() != null) profile.setSem2Sgpa(dto.getSem2Sgpa());
        if (dto.getSem3Sgpa() != null) profile.setSem3Sgpa(dto.getSem3Sgpa());
        if (dto.getSem4Sgpa() != null) profile.setSem4Sgpa(dto.getSem4Sgpa());
        if (dto.getSem5Sgpa() != null) profile.setSem5Sgpa(dto.getSem5Sgpa());
        if (dto.getSem6Sgpa() != null) profile.setSem6Sgpa(dto.getSem6Sgpa());
        if (dto.getSem7Sgpa() != null) profile.setSem7Sgpa(dto.getSem7Sgpa());
        if (dto.getSem8Sgpa() != null) profile.setSem8Sgpa(dto.getSem8Sgpa());

        if (dto.getPlacementStatus() != null) profile.setPlacementStatus(dto.getPlacementStatus());
        if (dto.getPlacedCompany() != null) profile.setPlacedCompany(dto.getPlacedCompany());
        if (dto.getPlacedRole() != null) profile.setPlacedRole(dto.getPlacedRole());
        if (dto.getPlacedCtc() != null) profile.setPlacedCtc(dto.getPlacedCtc());
        if (dto.getPackageCategory() != null) profile.setPackageCategory(dto.getPackageCategory());
        if (dto.getBondDuration() != null) profile.setBondDuration(dto.getBondDuration());
        if (dto.getOfferDate() != null) profile.setOfferDate(dto.getOfferDate());
        if (dto.getJoiningDate() != null) profile.setJoiningDate(dto.getJoiningDate());
        if (dto.getRecruitmentSource() != null) profile.setRecruitmentSource(dto.getRecruitmentSource());

        if (dto.getLinkedinUrl() != null) profile.setLinkedinUrl(dto.getLinkedinUrl());
        if (dto.getGithubUrl() != null) profile.setGithubUrl(dto.getGithubUrl());
        if (dto.getPortfolioUrl() != null) profile.setPortfolioUrl(dto.getPortfolioUrl());
    }
}
