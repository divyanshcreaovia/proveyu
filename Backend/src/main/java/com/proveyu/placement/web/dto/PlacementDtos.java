package com.proveyu.placement.web.dto;

import com.proveyu.placement.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PlacementDtos {

    // --- Company DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompanyRequest {
        @NotBlank(message = "Company name is required")
        private String name;
        private String category;
        private String website;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompanyResponse {
        private UUID companyId;
        private String name;
        private String category;
        private String website;
        private Instant createdAt;
    }

    // --- Branch DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchRequest {
        @NotBlank(message = "Branch name is required")
        private String branchName;
        @NotBlank(message = "Branch code is required")
        private String branchCode;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BranchResponse {
        private UUID branchId;
        private UUID collegeId;
        private String branchName;
        private String branchCode;
        private Instant createdAt;
    }

    // --- Tier DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TierRequest {
        @NotNull(message = "Tier level is required")
        private Integer tierLevel;
        @NotBlank(message = "Tier name is required")
        private String tierName;
        @NotNull(message = "Minimum score is required")
        private BigDecimal minScore;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TierResponse {
        private UUID tierId;
        private UUID collegeId;
        private int tierLevel;
        private String tierName;
        private BigDecimal minScore;
        private Instant createdAt;
    }

    // --- Package Tier Rule DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PackageTierRuleRequest {
        @NotNull(message = "Minimum package LPA is required")
        private BigDecimal minPackageLpa;
        private BigDecimal maxPackageLpa;
        @NotNull(message = "Tier ID is required")
        private UUID tierId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PackageTierRuleResponse {
        private UUID ruleId;
        private UUID collegeId;
        private BigDecimal minPackageLpa;
        private BigDecimal maxPackageLpa;
        private UUID tierId;
        private String tierName;
        private Instant createdAt;
    }

    // --- Drive DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateDriveRequest {
        @NotNull(message = "Company ID is required")
        private UUID companyId;
        @NotNull(message = "Offered package is required")
        private BigDecimal offeredPackage;
        private LocalDate driveDate;
        private String location;
        private PlacedPolicy placedPolicy;
        private DriveEligibilityDto eligibility;
        private List<UUID> tierIds;       // Optional manual override
        private List<UUID> branchIds;     // Optional, empty = all
        private List<String> roles;       // Job roles offered
        private List<UUID> roundIds;      // Initial rounds to configure
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateDriveRequest {
        private BigDecimal offeredPackage;
        private LocalDate driveDate;
        private String location;
        private PlacedPolicy placedPolicy;
        private DriveStatus status;
        private DriveEligibilityDto eligibility;
        private List<UUID> tierIds;
        private List<UUID> branchIds;
        private List<String> roles;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriveEligibilityDto {
        private BigDecimal minCgpa;
        private BigDecimal minScore;
        private BigDecimal min10thPercent;
        private BigDecimal min12thPercent;
        private Integer maxActiveBacklogs;
        private Integer maxGapYears;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriveResponse {
        private UUID driveId;
        private UUID collegeId;
        private UUID companyId;
        private String companyName;
        private BigDecimal offeredPackage;
        private LocalDate driveDate;
        private String location;
        private PlacedPolicy placedPolicy;
        private DriveStatus status;
        private DriveEligibilityDto eligibility;
        private List<TierResponse> tiers;
        private List<BranchResponse> branches;
        private List<String> roles;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // --- Eligible Student / Preview DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EligibleStudentResponse {
        private UUID studentId;
        private String fullName;
        private String email;
        private String phone;
        private String rollNumber;
        private UUID branchId;
        private String branchName;
        private BigDecimal cgpa;
        private BigDecimal score;
        private BigDecimal tenthPercent;
        private BigDecimal twelfthPercent;
        private int activeBacklogs;
        private int gapYears;
        private String tierName;
        private Integer tierLevel;
        private PlacedStatus placedStatus;
        private BigDecimal highestPackageLpa;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriveEligiblePreviewResponse {
        private int totalCount;
        private List<EligibleStudentResponse> students;
    }

    // --- Round DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateDriveRoundRequest {
        @NotNull(message = "Round ID is required")
        private UUID roundId;
        @NotNull(message = "Round number is required")
        private Integer roundNumber;
        private LocalDate roundDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriveRoundResponse {
        private UUID driveRoundId;
        private UUID driveId;
        private UUID roundId;
        private String roundName;
        private int roundNumber;
        private LocalDate roundDate;
        private RoundStatus status;
        private long shortlistedCount;
        private long attendedCount;
        private long selectedCount;
        private long rejectedCount;
        private long absentCount;
        private Instant createdAt;
    }

    // --- Round Student DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoundStudentResponse {
        private UUID id;
        private UUID driveRoundId;
        private UUID studentId;
        private String fullName;
        private String email;
        private String rollNumber;
        private String branchName;
        private RoundStudentStatus status;
        private BigDecimal score;
        private String remarks;
        private Instant mailSentAt;
        private Instant updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRoundStudentItem {
        @NotNull(message = "Student ID is required")
        private UUID studentId;
        @NotNull(message = "Status is required")
        private RoundStudentStatus status;
        private BigDecimal score;
        private String remarks;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BulkUpdateRoundStudentsRequest {
        @NotNull(message = "Student updates list is required")
        private List<UpdateRoundStudentItem> updates;
    }

    // --- Placement Offer DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateOffersRequest {
        private String roleName;
        private BigDecimal packageLpa; // Defaults to drive offered package if null
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateOfferStatusRequest {
        @NotNull(message = "Offer status is required")
        private OfferStatus offerStatus;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlacementOfferResponse {
        private UUID offerId;
        private UUID driveId;
        private String companyName;
        private UUID studentId;
        private String studentName;
        private String studentEmail;
        private String roleName;
        private BigDecimal packageLpa;
        private OfferStatus offerStatus;
        private Instant offeredAt;
    }

    // --- Student Academic & Bulk Import DTOs ---
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentAcademicRequest {
        private String fullName;
        private String email;
        private String phone;
        private String rollNumber;
        private UUID branchId;
        private BigDecimal cgpa;
        private BigDecimal score;
        private BigDecimal tenthPercent;
        private BigDecimal twelfthPercent;
        private Integer activeBacklogs;
        private Integer gapYears;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentImportRow {
        private String name;
        private String email;
        private String phone;
        private String rollNumber;
        private String branchCode;
        private BigDecimal cgpa;
        private BigDecimal score;
        private BigDecimal tenthPercent;
        private BigDecimal twelfthPercent;
        private Integer activeBacklogs;
        private Integer gapYears;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentImportResponse {
        private int totalProcessed;
        private int successCount;
        private int failureCount;
        private List<String> errors;
    }
}
