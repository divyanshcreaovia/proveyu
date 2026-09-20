package com.proveyu.recruiter.web;

import com.proveyu.recruiter.application.InterviewService;
import com.proveyu.recruiter.domain.InterviewInvite;
import com.proveyu.recruiter.web.dto.InterviewDto.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<InterviewInvite>> sendInvite(@Valid @RequestBody SendInviteRequest request,
                                                               Authentication authentication) {
        UUID recruiterId = UUID.fromString(authentication.getName());
        InterviewInvite invite = interviewService.sendInterviewInvite(
                recruiterId,
                request.getCandidateId(),
                request.getCompanyId(),
                request.getExamId(),
                request.getJobTitle(),
                request.getJobDescription(),
                request.getMinSalaryLpa(),
                request.getMaxSalaryLpa(),
                request.getPackageRange()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(invite, "Interview invitation sent successfully"));
    }

    @GetMapping("/candidate-invites")
    public ResponseEntity<ApiResponse<List<InterviewInvite>>> getCandidateInvites(Authentication authentication) {
        UUID candidateId = UUID.fromString(authentication.getName());
        List<InterviewInvite> list = interviewService.getCandidateInvites(candidateId);
        return ResponseEntity.ok(ApiResponse.success(list, "Candidate interview invitations retrieved successfully"));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<InterviewInvite>>> getSentInvites(Authentication authentication) {
        UUID recruiterId = UUID.fromString(authentication.getName());
        List<InterviewInvite> list = interviewService.getRecruiterSentInvites(recruiterId);
        return ResponseEntity.ok(ApiResponse.success(list, "Sent interview invitations retrieved successfully"));
    }

    @PutMapping("/{inviteId}/respond")
    public ResponseEntity<ApiResponse<InterviewInvite>> respondToInvite(@PathVariable UUID inviteId,
                                                                     @Valid @RequestBody RespondInviteRequest request,
                                                                     Authentication authentication) {
        UUID candidateId = UUID.fromString(authentication.getName());
        InterviewInvite invite = interviewService.respondToInterviewInvite(candidateId, inviteId, request.isAccept());
        return ResponseEntity.ok(ApiResponse.success(invite, "Interview invitation response saved successfully"));
    }

    @PutMapping("/{inviteId}/placement-status")
    public ResponseEntity<ApiResponse<InterviewInvite>> updatePlacementStatus(@PathVariable UUID inviteId,
                                                                           @Valid @RequestBody UpdatePlacementRequest request) {
        InterviewInvite invite = interviewService.updatePlacementStatus(inviteId, request.getPlacementStatus());
        return ResponseEntity.ok(ApiResponse.success(invite, "Candidate placement status updated successfully"));
    }
}
