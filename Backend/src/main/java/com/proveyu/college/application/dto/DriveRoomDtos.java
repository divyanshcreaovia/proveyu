package com.proveyu.college.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.domain.DriveRecruiter;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drive Room: company HR / panel access to one campus drive. */
public final class DriveRoomDtos {
    private DriveRoomDtos() {}

    // ------------------------------------------------------------------ TPO side

    public record RecruiterInviteRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 150) String fullName,
                                         @Size(max = 150) String designation, boolean shareDetails) {}

    public record RecruiterAccessUpdate(boolean shareDetails) {}

    public record RecruiterAccessResponse(UUID id, UUID driveId, String email, String fullName, String designation, DriveRecruiter.Status status,
                                          boolean shareDetails, Instant expiresAt, Instant acceptedAt, Instant lastAccessAt, Instant createdAt,
                                          String devInviteToken) {}

    // ------------------------------------------------------------------ invitation (public)

    public record RecruiterInviteInfo(String collegeName, String driveTitle, String companyName, String email, String fullName,
                                      boolean existingAccount, Instant expiresAt) {}

    public record RecruiterAcceptRequest(@NotBlank String token, @Size(max = 150) String fullName, @NotBlank String password) {}

    // ------------------------------------------------------------------ company side

    public record RoomDriveCard(UUID driveId, String collegeName, String companyName, String title, String roleTitle, DriveStatus status,
                                LocalDate driveDate, BigDecimal ctcLpa, long applicants, long shortlisted, long selected, long offers) {}

    public record RoomRound(UUID id, int seq, String name, RoundType roundType, Instant scheduledAt, RoundStatus status, Map<String, Long> resultCounts) {}

    public record RoomSummary(UUID driveId, String collegeName, String companyName, UUID companyId, String title, String roleTitle, DriveType driveType,
                              DriveStatus status, BigDecimal ctcLpa, BigDecimal stipendMonthly, String location, DriveMode mode, String venue,
                              LocalDate driveDate, Instant registrationDeadline, String jobDescription, boolean shareDetails, boolean canEdit,
                              Map<String, Long> funnel, List<RoomRound> rounds, String myName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoomApplicant(UUID applicationId, UUID studentId, String name, String rollNumber, String branch, String branchCode, Integer batchYear,
                                String degree, BigDecimal cgpa, Integer activeBacklogs, List<String> skills, String linkedinUrl, String githubUrl,
                                String email, String phone, BigDecimal tenthPercent, BigDecimal twelfthPercent, ApplicationStatus status,
                                int currentRound, boolean hasResume, Map<String, String> roundResults, OfferStatus offerStatus, UUID offerId,
                                Instant appliedAt) {}

    public record RoomStatusRequest(@NotEmpty List<UUID> applicationIds, @NotNull ApplicationStatus status, @Size(max = 500) String remarks) {}

    public record ScorecardRequest(@Min(1) @Max(5) Integer technical, @Min(1) @Max(5) Integer communication, @Min(1) @Max(5) Integer attitude,
                                   @NotNull RoundResultValue result, @Size(max = 1000) String remarks) {}

    public record ScorecardResponse(UUID id, UUID roundId, UUID applicationId, UUID userId, String interviewerName, Integer technical,
                                    Integer communication, Integer attitude, RoundResultValue result, String remarks, Instant updatedAt, boolean mine) {}

    public record RoomOfferRequest(@NotEmpty List<UUID> applicationIds, @Size(max = 150) String roleTitle, @DecimalMin("0") BigDecimal ctcLpa,
                                   @Size(max = 4000) String ctcBreakup, LocalDate offerDate, LocalDate joiningDate) {}

    public record RoomOfferResult(int created, List<String> errors) {}
}
