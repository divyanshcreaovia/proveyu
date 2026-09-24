package com.proveyu.organization.web;

import com.proveyu.auth.domain.User;
import com.proveyu.organization.application.OrganizationService;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.domain.OrganizationCandidate;
import com.proveyu.organization.web.dto.OrganizationDto.*;
import com.proveyu.recruiter.domain.CandidateSearchIndex;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<Organization>> createProfile(@Valid @RequestBody CreateOrgProfileRequest request,
                                                              Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        Organization organization = organizationService.createOrganizationProfile(
                userId,
                request.getLegalName(),
                request.getOrgType(),
                request.getCodeOrGstin(),
                request.getContactEmail(),
                request.getContactPhone(),
                request.getAddress()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(organization, "Organization profile updated successfully"));
    }

    @PostMapping("/candidates")
    public ResponseEntity<ApiResponse<User>> registerCandidate(@Valid @RequestBody RegisterCandidateRequest request,
                                                           Authentication authentication) {
        UUID orgUserId = UUID.fromString(authentication.getName());
        User candidate = organizationService.registerCandidateUnderOrganization(
                orgUserId,
                request.getEmail(),
                request.getFullName(),
                request.getPassword(),
                request.getRollNumberOrId(),
                request.getBatchYear()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(candidate, "Candidate onboarded successfully under organization"));
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<OrganizationCandidate>>> getCandidates(Authentication authentication) {
        UUID orgUserId = UUID.fromString(authentication.getName());
        List<OrganizationCandidate> list = organizationService.getOrganizationCandidates(orgUserId);
        return ResponseEntity.ok(ApiResponse.success(list, "Organization candidates fetched successfully"));
    }

    @GetMapping("/candidates/{candidateId}/scorecard")
    public ResponseEntity<ApiResponse<List<CandidateSearchIndex>>> getCandidateScorecard(@PathVariable UUID candidateId,
                                                                                     Authentication authentication) {
        UUID orgUserId = UUID.fromString(authentication.getName());
        List<CandidateSearchIndex> scorecard = organizationService.getCandidateScorecards(orgUserId, candidateId);
        return ResponseEntity.ok(ApiResponse.success(scorecard, "Candidate scorecard fetched successfully"));
    }
}
