package com.proveyu.placement.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.placement.domain.Branch;
import com.proveyu.placement.domain.PlacedStatus;
import com.proveyu.placement.domain.Student;
import com.proveyu.placement.domain.Tier;
import com.proveyu.placement.infrastructure.BranchRepository;
import com.proveyu.placement.infrastructure.StudentRepository;
import com.proveyu.placement.web.dto.PlacementDtos.EligibleStudentResponse;
import com.proveyu.placement.web.dto.PlacementDtos.StudentAcademicRequest;
import com.proveyu.placement.web.dto.PlacementDtos.StudentImportRow;
import com.proveyu.placement.web.dto.PlacementDtos.StudentImportResponse;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementStudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final OrganizationRepository organizationRepository;
    private final PlacementTierService tierService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public EligibleStudentResponse registerOrUpdateStudent(UUID collegeId, StudentAcademicRequest request) {
        Organization org = organizationRepository.findById(collegeId)
                .orElseThrow(() -> new DomainException("College organization not found", HttpStatus.NOT_FOUND, "COLLEGE_NOT_FOUND"));

        Branch branch = branchRepository.findByCollegeIdAndBranchId(collegeId, request.getBranchId())
                .orElseThrow(() -> new DomainException("Branch not found under this college", HttpStatus.BAD_REQUEST, "BRANCH_NOT_FOUND"));

        boolean isNewUser = false;
        String rawPassword = null;

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            isNewUser = true;
            rawPassword = generateRandomPassword();
            user = User.builder()
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(Role.CANDIDATE)
                    .active(true)
                    .build();
            user = userRepository.save(user);
        } else {
            if (request.getFullName() != null) user.setFullName(request.getFullName());
            if (request.getPhone() != null) user.setPhone(request.getPhone());
            user = userRepository.save(user);
        }

        Student student = studentRepository.findByStudentId(user.getId()).orElse(
                Student.builder()
                        .studentId(user.getId())
                        .collegeId(collegeId)
                        .branchId(branch.getBranchId())
                        .placedStatus(PlacedStatus.UNPLACED)
                        .build()
        );

        student.setCollegeId(collegeId);
        student.setBranchId(branch.getBranchId());
        student.setRollNumber(request.getRollNumber());
        student.setCgpa(request.getCgpa());
        student.setScore(request.getScore());
        student.setTenthPercent(request.getTenthPercent());
        student.setTwelfthPercent(request.getTwelfthPercent());
        student.setActiveBacklogs(request.getActiveBacklogs() != null ? request.getActiveBacklogs() : 0);
        student.setGapYears(request.getGapYears() != null ? request.getGapYears() : 0);

        Student savedStudent = studentRepository.save(student);

        if (isNewUser) {
            emailService.sendStudentCredentialsEmail(user.getEmail(), user.getFullName(), rawPassword, org.getLegalName());
        }

        return toEligibleStudentResponse(savedStudent, user, branch.getBranchName());
    }

    @Transactional
    public StudentImportResponse bulkImportStudents(UUID collegeId, List<StudentImportRow> rows) {
        Organization org = organizationRepository.findById(collegeId)
                .orElseThrow(() -> new DomainException("College organization not found", HttpStatus.NOT_FOUND, "COLLEGE_NOT_FOUND"));

        List<Branch> branches = branchRepository.findByCollegeId(collegeId);
        Map<String, Branch> branchMap = branches.stream()
                .collect(Collectors.toMap(b -> b.getBranchCode().trim().toUpperCase(), b -> b, (b1, b2) -> b1));

        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            StudentImportRow row = rows.get(i);
            try {
                if (row.getEmail() == null || row.getEmail().isBlank()) {
                    throw new IllegalArgumentException("Email is required");
                }
                if (row.getName() == null || row.getName().isBlank()) {
                    throw new IllegalArgumentException("Name is required");
                }
                Branch branch = null;
                if (row.getBranchCode() != null && !row.getBranchCode().isBlank()) {
                    branch = branchMap.get(row.getBranchCode().trim().toUpperCase());
                }
                if (branch == null && !branches.isEmpty()) {
                    branch = branches.get(0); // fallback to primary branch
                }
                if (branch == null) {
                    throw new IllegalArgumentException("No valid branch found for branch code: " + row.getBranchCode());
                }

                boolean isNewUser = false;
                String rawPassword = null;
                User user = userRepository.findByEmail(row.getEmail().trim().toLowerCase()).orElse(null);
                if (user == null) {
                    isNewUser = true;
                    rawPassword = generateRandomPassword();
                    user = User.builder()
                            .email(row.getEmail().trim().toLowerCase())
                            .fullName(row.getName().trim())
                            .phone(row.getPhone())
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .role(Role.CANDIDATE)
                            .active(true)
                            .build();
                    user = userRepository.save(user);
                }

                Student student = studentRepository.findByStudentId(user.getId()).orElse(
                        Student.builder()
                                .studentId(user.getId())
                                .collegeId(collegeId)
                                .branchId(branch.getBranchId())
                                .placedStatus(PlacedStatus.UNPLACED)
                                .build()
                );

                student.setCollegeId(collegeId);
                student.setBranchId(branch.getBranchId());
                student.setRollNumber(row.getRollNumber());
                student.setCgpa(row.getCgpa());
                student.setScore(row.getScore());
                student.setTenthPercent(row.getTenthPercent());
                student.setTwelfthPercent(row.getTwelfthPercent());
                student.setActiveBacklogs(row.getActiveBacklogs() != null ? row.getActiveBacklogs() : 0);
                student.setGapYears(row.getGapYears() != null ? row.getGapYears() : 0);

                studentRepository.save(student);

                if (isNewUser) {
                    emailService.sendStudentCredentialsEmail(user.getEmail(), user.getFullName(), rawPassword, org.getLegalName());
                }

                successCount++;
            } catch (Exception ex) {
                failureCount++;
                errors.add("Row " + (i + 1) + " (" + row.getEmail() + "): " + ex.getMessage());
                log.warn("[BULK IMPORT ERROR] Failed for row {}: {}", i + 1, ex.getMessage());
            }
        }

        return StudentImportResponse.builder()
                .totalProcessed(rows.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EligibleStudentResponse> listStudents(UUID collegeId) {
        List<Student> students = studentRepository.findByCollegeId(collegeId);
        List<Branch> branches = branchRepository.findByCollegeId(collegeId);
        Map<UUID, String> branchNameMap = branches.stream()
                .collect(Collectors.toMap(Branch::getBranchId, Branch::getBranchName));

        List<UUID> userIds = students.stream().map(Student::getStudentId).collect(Collectors.toList());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return students.stream().map(student -> {
            User user = userMap.get(student.getStudentId());
            String branchName = branchNameMap.getOrDefault(student.getBranchId(), "Unknown Branch");
            return toEligibleStudentResponse(student, user, branchName);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EligibleStudentResponse getStudent(UUID collegeId, UUID studentId) {
        Student student = studentRepository.findByCollegeIdAndStudentId(collegeId, studentId)
                .orElseThrow(() -> new DomainException("Student not found", HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND"));

        User user = userRepository.findById(student.getStudentId()).orElse(null);
        String branchName = branchRepository.findById(student.getBranchId())
                .map(Branch::getBranchName)
                .orElse("Unknown Branch");

        return toEligibleStudentResponse(student, user, branchName);
    }

    private EligibleStudentResponse toEligibleStudentResponse(Student student, User user, String branchName) {
        Optional<Tier> tierOpt = tierService.deriveTier(student.getCollegeId(), student.getScore());

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
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CHAR_POOL.charAt(RANDOM.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
