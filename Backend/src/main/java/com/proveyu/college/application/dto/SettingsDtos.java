package com.proveyu.college.application.dto;

import com.proveyu.college.domain.CollegeEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record ProfileResponse(UUID collegeId, String collegeName, String aicteCode, String university, String city, String state,
                                  String emailDomain, String website, String tpoName, String tpoEmail, String tpoPhone, ApprovalStatus approvalStatus,
                                  String currentAcademicYear, boolean hasLogo, String brandColor) {}

    public record ProfileRequest(@NotBlank @Size(max = 200) String collegeName, @Size(max = 200) String university, @Size(max = 100) String city,
                                 @Size(max = 100) String state, @Size(max = 255) String website, @NotBlank @Size(max = 150) String tpoName,
                                 @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Invalid phone") String tpoPhone,
                                 @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Use format 2025-26") String currentAcademicYear,
                                 @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Use a hex colour like #2563eb") String brandColor) {}

    public record PolicyDto(@NotNull PolicyMode policyMode, @Min(1) @Max(200) Integer maxApplications, @NotNull @Min(1) @Max(10) Integer maxOffers,
                            @NotNull @DecimalMin("0") @DecimalMax("500") BigDecimal upgradeMinHikePercent, boolean blockAfterAccept) {}

    public record BranchRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Letters, numbers, - and _ only") String code,
                                @NotBlank @Size(max = 150) String name) {}

    public record BranchResponse(UUID id, String code, String name, long students) {}

    public record BatchRequest(@NotNull @Min(2000) @Max(2100) Integer gradYear, @NotBlank @Size(max = 50) String label, boolean active) {}

    public record BatchResponse(UUID id, int gradYear, String label, boolean active, long students) {}

    public record TierDto(UUID id, @NotBlank @Size(max = 50) String name, @NotNull @Min(1) @Max(10) Integer tierRank,
                          @NotNull @DecimalMin("0") BigDecimal minCtcLpa) {}

    public record TiersRequest(@NotEmpty @Size(max = 6) List<@Valid TierDto> tiers) {}

    public record TemplateDto(String key, @NotBlank @Size(max = 255) String subject, @NotBlank @Size(max = 20000) String body, boolean customized) {}
}
