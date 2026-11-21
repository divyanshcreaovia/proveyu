package com.proveyu.enquiry.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pricing enquiries from the public website: who wants ProveYu, so the team can call them back. */
public final class EnquiryModel {
    private EnquiryModel() {}

    public enum OrgType { COLLEGE, UNIVERSITY, COMPANY, OTHER }
    public enum Status { NEW, CONTACTED, CLOSED }

    /** The public form. {@code website} is a hidden field people never fill: a value means a bot, and the enquiry is dropped. */
    public record NewEnquiry(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
            @NotBlank @Size(max = 255) String organization,
            @NotNull OrgType orgType,
            @Size(max = 120) String designation,
            @Size(max = 120) String city,
            @Pattern(regexp = "^(|UNDER_500|500_1000|1000_3000|3000_PLUS)$", message = "Choose a student range") String studentCount,
            @Size(max = 2000) String message,
            @Size(max = 200) String website) {}

    public record StatusChange(@NotNull Status status, @Size(max = 2000) String note) {}

    public record Enquiry(UUID id, String fullName, String email, String phone, String organization, OrgType orgType, String designation,
                          String city, String studentCount, String message, Status status, String adminNote, String handledBy,
                          Instant createdAt, Instant updatedAt) {}
    public record EnquiryPage(List<Enquiry> rows, long total, int page, int size) {}
}
