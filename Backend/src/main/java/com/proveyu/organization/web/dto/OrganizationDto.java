package com.proveyu.organization.web.dto;

import com.proveyu.organization.domain.OrgType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class OrganizationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrgProfileRequest {
        @NotBlank(message = "Legal name is required")
        private String legalName;
        private OrgType orgType = OrgType.COLLEGE;
        private String codeOrGstin;
        private String contactEmail;
        private String contactPhone;
        private String address;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterCandidateRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Full name is required")
        private String fullName;

        private String password;
        private String rollNumberOrId;
        private String batchYear;
    }
}
