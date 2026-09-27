package com.proveyu.recruiter.web;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.recruiter.application.ManageHiringService;
import com.proveyu.recruiter.domain.JobStatus;
import com.proveyu.recruiter.web.dto.JobDto.*;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter/jobs")
@RequiredArgsConstructor
public class ManageHiringController {

    private final ManageHiringService manageHiringService;
    private final UserRepository userRepository;

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
            java.util.Optional<User> userOpt = userRepository.findByEmail(authentication.getName());
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            }
        }
        throw new DomainException("User authentication required. Please log in.", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    private UUID resolveRecruiterId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
            java.util.Optional<User> userOpt = userRepository.findByEmail(authentication.getName());
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            }
        }
        throw new DomainException("Recruiter authentication required. Please log in.", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody CreateJobRequest request,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        JobResponse response = manageHiringService.createJob(recruiterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Job requirement posted successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobResponse>>> getRecruiterJobs(Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        List<JobResponse> jobs = manageHiringService.getRecruiterJobs(recruiterId);
        return ResponseEntity.ok(ApiResponse.success(jobs, "Recruiter posted jobs retrieved successfully"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJobById(@PathVariable UUID jobId) {
        JobResponse job = manageHiringService.getJobById(jobId);
        return ResponseEntity.ok(ApiResponse.success(job, "Job details retrieved successfully"));
    }

    @GetMapping("/{jobId}/matches")
    public ResponseEntity<ApiResponse<List<CandidateMatchResponse>>> getJobMatches(@PathVariable UUID jobId) {
        List<CandidateMatchResponse> matches = manageHiringService.getJobMatches(jobId);
        return ResponseEntity.ok(ApiResponse.success(matches, "Verified candidates matching job domain retrieved successfully"));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobRequest request,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        JobResponse updated = manageHiringService.updateJob(jobId, recruiterId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Job requirement updated successfully"));
    }

    @PatchMapping("/{jobId}/status")
    public ResponseEntity<ApiResponse<JobResponse>> updateJobStatus(
            @PathVariable UUID jobId,
            @RequestParam JobStatus status,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        JobResponse updated = manageHiringService.updateJobStatus(jobId, recruiterId, status);
        return ResponseEntity.ok(ApiResponse.success(updated, "Job status updated to " + status));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @PathVariable UUID jobId,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        manageHiringService.deleteJob(jobId, recruiterId);
        return ResponseEntity.ok(ApiResponse.success(null, "Job requirement deleted successfully"));
    }

    @PostMapping("/shortlist")
    public ResponseEntity<ApiResponse<JobInvitationResponse>> shortlistCandidate(
            @Valid @RequestBody ShortlistCandidateRequest request,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        JobInvitationResponse response = manageHiringService.shortlistCandidate(recruiterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Candidate marked as shortlisted successfully"));
    }

    @PostMapping("/{jobId}/invite")
    public ResponseEntity<ApiResponse<JobInvitationResponse>> sendJobInvitation(
            @PathVariable UUID jobId,
            @Valid @RequestBody SendJobInvitationRequest request,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        JobInvitationResponse response = manageHiringService.sendJobInvitation(jobId, recruiterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Job invitation dispatched to candidate successfully"));
    }

    @GetMapping("/{jobId}/invitations")
    public ResponseEntity<ApiResponse<List<JobInvitationResponse>>> getJobInvitations(
            @PathVariable UUID jobId,
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        List<JobInvitationResponse> invitations = manageHiringService.getJobInvitations(jobId, recruiterId);
        return ResponseEntity.ok(ApiResponse.success(invitations, "Job hiring pipeline candidates retrieved"));
    }

    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<List<JobInvitationResponse>>> getAllRecruiterInvitations(
            Authentication authentication) {
        UUID recruiterId = resolveRecruiterId(authentication);
        List<JobInvitationResponse> invitations = manageHiringService.getRecruiterAllInvitations(recruiterId);
        return ResponseEntity.ok(ApiResponse.success(invitations, "All candidate invitations retrieved"));
    }

    @PutMapping("/invitations/{invitationId}/status")
    public ResponseEntity<ApiResponse<JobInvitationResponse>> updateInvitationStatus(
            @PathVariable UUID invitationId,
            @Valid @RequestBody UpdateJobInvitationStatusRequest request,
            Authentication authentication) {
        UUID userId = resolveRecruiterId(authentication);
        JobInvitationResponse response = manageHiringService.updateInvitationStatus(invitationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Job invitation status updated successfully"));
    }

    @PutMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<ApiResponse<JobInvitationResponse>> acceptInvitation(
            @PathVariable UUID invitationId,
            Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        UpdateJobInvitationStatusRequest req = UpdateJobInvitationStatusRequest.builder()
                .status(com.proveyu.recruiter.domain.JobInvitationStatus.ACCEPT)
                .notes("Accepted by candidate")
                .build();
        JobInvitationResponse response = manageHiringService.updateInvitationStatus(invitationId, userId, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Job invitation accepted successfully"));
    }

    @PutMapping("/invitations/{invitationId}/decline")
    public ResponseEntity<ApiResponse<JobInvitationResponse>> declineInvitation(
            @PathVariable UUID invitationId,
            Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        UpdateJobInvitationStatusRequest req = UpdateJobInvitationStatusRequest.builder()
                .status(com.proveyu.recruiter.domain.JobInvitationStatus.DECLINE)
                .notes("Declined by candidate")
                .build();
        JobInvitationResponse response = manageHiringService.updateInvitationStatus(invitationId, userId, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Job invitation declined successfully"));
    }

    @GetMapping("/candidate/invitations")
    public ResponseEntity<ApiResponse<List<JobInvitationResponse>>> getCandidateInvitations(
            @RequestParam(required = false) UUID candidateId,
            Authentication authentication) {
        UUID resolvedCandidateId = candidateId;
        if (resolvedCandidateId == null) {
            resolvedCandidateId = resolveCurrentUserId(authentication);
        }
        List<JobInvitationResponse> invitations = manageHiringService.getCandidateInvitations(resolvedCandidateId);
        return ResponseEntity.ok(ApiResponse.success(invitations, "Candidate received invitations retrieved"));
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<CandidateMatchResponse>>> getAllCandidates(
            @RequestParam(required = false) UUID domainId) {
        List<CandidateMatchResponse> candidates = manageHiringService.getAllCandidates(domainId);
        return ResponseEntity.ok(ApiResponse.success(candidates, "Verified candidate profiles retrieved successfully"));
    }
}
