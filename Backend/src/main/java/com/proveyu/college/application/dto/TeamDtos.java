package com.proveyu.college.application.dto;

import com.proveyu.college.domain.CollegeEnums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {}

    public record CollegeRegisterRequest(
            @NotBlank @Size(max = 200) String collegeName,
            @NotBlank @Size(max = 50) String aicteCode,
            @NotBlank @Size(max = 200) String university,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Enter a domain like college.edu.in") String emailDomain,
            @Size(max = 255) String website,
            @NotBlank @Size(max = 150) String tpoName,
            @NotBlank @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Invalid phone number") String tpoPhone,
            @NotBlank @Email @Size(max = 190) String tpoEmail,
            @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers") String password,
            @AssertTrue(message = "You must accept the terms") boolean acceptTerms) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegisterResponse(UUID collegeId, String email, ApprovalStatus approvalStatus, String message, String devVerificationToken) {}

    public record InviteInfo(String collegeName, String email, String fullName, MemberRole role, Instant expiresAt) {}

    public record AcceptInviteRequest(@NotBlank String token,
                                      @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers") String password,
                                      @Size(max = 150) String fullName, @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Invalid phone number") String phone) {}

    public record MeResponse(UUID userId, String fullName, String email, MemberRole role, String designation, List<UUID> branchScope,
                             UUID collegeId, String collegeName, ApprovalStatus approvalStatus, String approvalNote, boolean hasLogo, String brandColor) {}

    public record MemberResponse(UUID id, UUID userId, String fullName, String email, String phone, MemberRole role, String designation,
                                 List<UUID> branchScope, List<String> branchNames, MemberStatus status, Instant lastLoginAt, Instant createdAt) {}

    public record InviteRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 150) String fullName, @NotNull MemberRole role,
                                @Size(max = 150) String designation, List<UUID> branchIds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InviteResponse(UUID id, String email, String fullName, MemberRole role, String designation, List<UUID> branchScope,
                                 Instant expiresAt, Instant createdAt, String devInviteToken) {}

    public record MemberUpdateRequest(@NotNull MemberRole role, @Size(max = 150) String designation, List<UUID> branchIds) {}

    public record RoleMatrixRow(String permission, boolean admin, boolean coordinator, boolean viewer) {}
}
