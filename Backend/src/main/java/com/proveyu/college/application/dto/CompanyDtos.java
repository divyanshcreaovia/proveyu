package com.proveyu.college.application.dto;

import com.proveyu.college.domain.CollegeEnums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CompanyDtos {
    private CompanyDtos() {}

    public record CompanyRequest(@NotBlank @Size(max = 200) String name, @Size(max = 100) String industry,
                                 @Size(max = 255) String website, @Size(max = 150) String hqLocation,
                                 MouStatus mouStatus, LocalDate mouExpiry, UUID linkedRecruiterId, @Size(max = 4000) String notes) {}

    public record CompanyResponse(UUID id, String name, String industry, String website, String hqLocation, MouStatus mouStatus,
                                  LocalDate mouExpiry, UUID linkedRecruiterId, String linkedRecruiterName, String notes,
                                  int drivesCount, int offersCount, Double avgRating, String primaryContactName, String primaryContactEmail,
                                  boolean deleted, Instant createdAt) {}

    public record ContactRequest(@NotBlank @Size(max = 150) String name, @Size(max = 150) String designation,
                                 @Email @Size(max = 190) String email, @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Invalid phone") String phone,
                                 boolean primaryContact) {}

    public record ContactResponse(UUID id, String name, String designation, String email, String phone, boolean primaryContact) {}

    public record FeedbackRequest(UUID driveId, @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}

    public record FeedbackResponse(UUID id, UUID driveId, String driveTitle, int rating, String comment, String createdByName, Instant createdAt) {}

    public record VisitHistory(UUID driveId, String title, String roleTitle, DriveStatus status, LocalDate driveDate, BigDecimal ctcLpa,
                               int applicants, int offers) {}

    public record CompanyDetail(CompanyResponse company, List<ContactResponse> contacts, List<VisitHistory> visits, List<FeedbackResponse> feedback) {}

    public record RecruiterOption(UUID userId, String fullName, String email) {}

    public record InviteCompanyRequest(@NotBlank @Email String email, @Size(max = 2000) String message) {}
}
