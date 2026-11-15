package com.proveyu.college.application.dto;

import com.proveyu.college.domain.CollegeEnums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StudentDtos {
    private StudentDtos() {}

    public record StudentRequest(
            @NotBlank @Size(max = 50) String rollNumber,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 190) String email,
            @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Invalid phone number") String phone,
            @Size(max = 20) String gender,
            @Past LocalDate dob,
            @NotNull UUID branchId,
            @NotNull @Min(2000) @Max(2100) Integer batchYear,
            @Size(max = 80) String degree,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cgpa,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal tenthPercent,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal twelfthPercent,
            @Min(0) @Max(50) Integer activeBacklogs,
            @Min(0) @Max(20) Integer gapYears,
            List<@Size(max = 60) String> skills,
            @Size(max = 255) String linkedinUrl,
            @Size(max = 255) String githubUrl) {}

    public record StudentResponse(UUID id, String rollNumber, String firstName, String lastName, String fullName, String email,
                                  String phone, String gender, LocalDate dob, UUID branchId, String branchName, String branchCode,
                                  Integer batchYear, String degree, BigDecimal cgpa, BigDecimal tenthPercent, BigDecimal twelfthPercent,
                                  int activeBacklogs, int gapYears, List<String> skills, String linkedinUrl, String githubUrl,
                                  StudentStatus status, BigDecimal highestCtcLpa, Integer highestTierRank, boolean hasAccount,
                                  boolean deleted, Instant createdAt, Instant updatedAt) {}

    public record StatusChangeRequest(@NotNull StudentStatus status, @NotBlank @Size(max = 500) String reason) {}

    public record DocumentResponse(UUID id, DocType docType, UUID fileId, String fileName, String contentType, long sizeBytes,
                                   VerificationStatus verificationStatus, String remarks, Instant verifiedAt, Instant createdAt) {}

    public record VerifyDocumentRequest(@NotNull VerificationStatus status, @Size(max = 500) String remarks) {}

    public record StudentApplicationSummary(UUID applicationId, UUID driveId, String driveTitle, String companyName,
                                            ApplicationStatus status, int currentRound, Instant appliedAt) {}

    public record StudentOfferSummary(UUID offerId, String companyName, String roleTitle, BigDecimal ctcLpa, OfferStatus status, LocalDate offerDate) {}

    public record SkillScore(String skill, double score, double maxScore, boolean passed) {}

    public record Passport(List<SkillScore> verifiedScores, Double averagePercent, String resumeUrl, String headline,
                           List<String> candidateSkills, int profileCompleteness, List<String> missingProfileItems) {}

    public record StudentDetail(StudentResponse student, List<DocumentResponse> documents, List<StudentApplicationSummary> applications,
                                List<StudentOfferSummary> offers, Passport passport) {}

    public record StudentFilter(String q, Integer batchYear, UUID branchId, BigDecimal minCgpa, BigDecimal maxCgpa, Integer maxBacklogs,
                                StudentStatus status, Boolean placed, String skill, boolean deleted) {}
}
