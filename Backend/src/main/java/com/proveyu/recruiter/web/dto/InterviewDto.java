package com.proveyu.recruiter.web.dto;

import com.proveyu.recruiter.domain.PlacementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class InterviewDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendInviteRequest {
        @NotNull(message = "Candidate ID is required")
        private UUID candidateId;

        private UUID companyId;
        private UUID examId;

        @NotBlank(message = "Job title is required")
        private String jobTitle;

        private String jobDescription;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String packageRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RespondInviteRequest {
        private boolean accept;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePlacementRequest {
        @NotNull(message = "Placement status is required")
        private PlacementStatus placementStatus;
    }
}
