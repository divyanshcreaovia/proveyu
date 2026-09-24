package com.proveyu.verification.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CompanyVerificationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyGstRequest {
        @NotBlank(message = "Legal company name is required")
        private String legalName;

        private String tradeName;

        @NotBlank(message = "GSTIN is required")
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "Invalid 15-character GSTIN format")
        private String gstin;

        private String registeredAddress;
        private String state;
        private String websiteDomain;
    }
}
