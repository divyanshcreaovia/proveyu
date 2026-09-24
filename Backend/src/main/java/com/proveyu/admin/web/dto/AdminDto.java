package com.proveyu.admin.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class AdminDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAdminRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExamRequest {
        @NotNull(message = "Skill ID is required")
        private UUID skillId;

        @Builder.Default
        private short level = 1;

        @NotBlank(message = "Title is required")
        private String title;

        @Builder.Default
        private short durationMinutes = 60;

        @Builder.Default
        private int totalMarks = 100;

        @Builder.Default
        private int passingMarks = 40;

        @Min(value = 0, message = "Price cannot be negative")
        @Builder.Default
        private int priceCents = 149900;

        @Builder.Default
        private String currency = "INR";
    }
}
