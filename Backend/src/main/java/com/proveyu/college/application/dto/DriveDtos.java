package com.proveyu.college.application.dto;

import com.proveyu.college.domain.CollegeEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DriveDtos {
    private DriveDtos() {}

    // ---------------------------------------------------------------- drives

    public record Eligibility(@DecimalMin("0") @DecimalMax("10") BigDecimal minCgpa, @DecimalMin("0") @DecimalMax("100") BigDecimal minTenth,
                              @DecimalMin("0") @DecimalMax("100") BigDecimal minTwelfth, @Min(0) Integer maxActiveBacklogs,
                              @Min(0) Integer maxGapYears, List<Integer> batchYears, List<UUID> branchIds, List<String> requiredSkills) {}

    public record DriveRequest(@NotNull UUID companyId, @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 150) String roleTitle,
                               @NotNull DriveType driveType, @DecimalMin("0") BigDecimal ctcLpa, @DecimalMin("0") BigDecimal stipendMonthly,
                               @Size(max = 4000) String ctcBreakup, @Size(max = 200) String location, @Min(0) @Max(120) Integer bondMonths,
                               @Size(max = 500) String bondDetails, @Size(max = 20000) String jobDescription, List<String> skills,
                               DriveMode mode, @Size(max = 255) String venue, Instant registrationDeadline, LocalDate driveDate, UUID tierId,
                               @Valid Eligibility eligibility) {}

    public record DriveResponse(UUID id, UUID companyId, String companyName, String title, String roleTitle, DriveType driveType,
                                BigDecimal ctcLpa, BigDecimal stipendMonthly, String ctcBreakup, String location, int bondMonths,
                                String bondDetails, String jobDescription, List<String> skills, DriveMode mode, String venue,
                                Instant registrationDeadline, LocalDate driveDate, UUID tierId, String tierName, Integer tierRank,
                                DriveStatus status, Eligibility eligibility, Instant publishedAt, boolean deleted, Instant createdAt,
                                Map<String, Long> applicationCounts, int roundsCount, int offersCount) {}

    public record TransitionRequest(@NotNull DriveStatus status, @Size(max = 500) String reason) {}

    // ---------------------------------------------------------------- eligibility

    public record EligibilityRow(UUID studentId, String rollNumber, String name, String branchCode, Integer batchYear, BigDecimal cgpa,
                                 int activeBacklogs, StudentStatus studentStatus, boolean eligible, boolean overridden, List<String> reasons,
                                 ApplicationStatus applicationStatus) {}

    public record EligibilityPreview(long totalStudents, long eligible, long ineligible, long applied, int page, int size, long totalElements,
                                     int totalPages, List<EligibilityRow> content) {}

    public record OverrideRequest(@NotNull UUID studentId, @NotNull OverrideAction action, @NotBlank @Size(min = 5, max = 500) String reason) {}

    public record OverrideResponse(UUID id, UUID studentId, String rollNumber, String studentName, OverrideAction action, String reason, Instant createdAt) {}

    // ---------------------------------------------------------------- applications

    public record ApplicationResponse(UUID id, UUID driveId, UUID studentId, String rollNumber, String studentName, String email,
                                      String branchCode, Integer batchYear, BigDecimal cgpa, ApplicationStatus status, ApplicationSource source,
                                      int currentRound, String remarks, Instant appliedAt) {}

    public record AddApplicantsRequest(@NotEmpty List<UUID> studentIds, @Size(max = 500) String overrideReason) {}

    public record BulkStatusRequest(@NotEmpty List<UUID> applicationIds, @NotNull ApplicationStatus status, @Size(max = 500) String remarks) {}

    public record BulkResult(int updated, List<String> skipped) {}

    // ---------------------------------------------------------------- rounds

    public record RoundRequest(@NotBlank @Size(max = 120) String name, @NotNull RoundType roundType, Instant scheduledAt) {}

    public record RoundResponse(UUID id, UUID driveId, int seq, String name, RoundType roundType, Instant scheduledAt, RoundStatus status,
                                Map<String, Long> resultCounts) {}

    public record ResultEntry(@NotNull UUID applicationId, @NotNull RoundResultValue result, @DecimalMin("0") BigDecimal score,
                              @Size(max = 500) String remarks) {}

    public record ResultsRequest(@NotEmpty List<@Valid ResultEntry> results) {}

    public record RoundResultRow(UUID resultId, UUID applicationId, UUID studentId, String rollNumber, String studentName, String branchCode,
                                 RoundResultValue result, BigDecimal score, String remarks) {}

    public record RoundUploadResult(int updated, List<String> errors) {}

    public record ReorderRequest(@NotEmpty List<UUID> roundIds) {}

    // ---------------------------------------------------------------- interviews

    public record SlotRequest(@NotNull UUID driveId, UUID roundId, @NotNull UUID applicationId, @Size(max = 150) String panelName,
                              @Size(max = 500) String panelMembers, @NotNull Instant startsAt, @NotNull Instant endsAt,
                              @Size(max = 100) String room, @Size(max = 500) String meetingLink) {}

    public record BulkSlotRequest(@NotNull UUID driveId, UUID roundId, @NotEmpty List<UUID> applicationIds, @NotNull Instant startAt,
                                  @NotNull @Min(5) @Max(480) Integer durationMinutes, @Min(0) @Max(120) Integer gapMinutes,
                                  @Size(max = 150) String panelName, @Size(max = 500) String panelMembers, @Size(max = 100) String room,
                                  @Size(max = 500) String meetingLink) {}

    public record SlotStatusRequest(@NotNull SlotStatus status) {}

    public record SlotResponse(UUID id, UUID driveId, String driveTitle, String companyName, UUID roundId, String roundName, UUID applicationId,
                               UUID studentId, String studentName, String rollNumber, String panelName, String panelMembers, Instant startsAt,
                               Instant endsAt, String room, String meetingLink, SlotStatus status, int rescheduleCount, Instant reminderSentAt) {}

    public record Conflict(String type, String message, UUID slotId) {}

    // ---------------------------------------------------------------- offers

    public record OfferRequest(@NotNull UUID studentId, UUID driveId, @NotNull UUID companyId, @NotBlank @Size(max = 150) String roleTitle,
                               @NotNull @DecimalMin("0") BigDecimal ctcLpa, @Size(max = 4000) String ctcBreakup, LocalDate offerDate,
                               LocalDate joiningDate, @Size(max = 500) String remarks, boolean overridePolicy, @Size(max = 500) String overrideReason) {}

    public record OfferStatusRequest(@NotNull OfferStatus status, @Size(max = 500) String remarks) {}

    public record OfferResponse(UUID id, UUID studentId, String rollNumber, String studentName, String branchCode, Integer batchYear,
                                UUID driveId, String driveTitle, UUID companyId, String companyName, String roleTitle, BigDecimal ctcLpa,
                                String ctcBreakup, Integer tierRank, String tierName, LocalDate offerDate, LocalDate joiningDate,
                                OfferStatus status, UUID offerLetterFileId, String remarks, Instant createdAt) {}
}
