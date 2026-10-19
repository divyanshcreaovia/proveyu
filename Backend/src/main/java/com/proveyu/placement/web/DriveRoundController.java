package com.proveyu.placement.web;

import com.proveyu.placement.application.DriveRoundService;
import com.proveyu.placement.domain.RoundStudentStatus;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class DriveRoundController {

    private final DriveRoundService roundService;
    private final CollegeContextResolver contextResolver;

    @PostMapping("/drives/{driveId}/rounds")
    public ResponseEntity<ApiResponse<DriveRoundResponse>> addRound(@PathVariable UUID driveId,
                                                                    @Valid @RequestBody CreateDriveRoundRequest request,
                                                                    @RequestParam(required = false) UUID collegeId,
                                                                    Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveRoundResponse response = roundService.addRound(effectiveCollegeId, driveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Round added successfully to drive"));
    }

    @GetMapping("/drives/{driveId}/rounds")
    public ResponseEntity<ApiResponse<List<DriveRoundResponse>>> listRounds(@PathVariable UUID driveId,
                                                                            @RequestParam(required = false) UUID collegeId,
                                                                            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<DriveRoundResponse> list = roundService.listRoundsForDrive(effectiveCollegeId, driveId);
        return ResponseEntity.ok(ApiResponse.success(list, "Drive rounds fetched successfully"));
    }

    @PostMapping("/drive-rounds/{driveRoundId}/complete")
    public ResponseEntity<ApiResponse<DriveRoundResponse>> completeRound(@PathVariable UUID driveRoundId,
                                                                         @RequestParam(required = false) UUID collegeId,
                                                                         Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveRoundResponse response = roundService.completeRound(effectiveCollegeId, driveRoundId);
        return ResponseEntity.ok(ApiResponse.success(response, "Drive round marked as completed"));
    }

    @GetMapping("/drive-rounds/{driveRoundId}/students")
    public ResponseEntity<ApiResponse<List<RoundStudentResponse>>> listStudentsInRound(
            @PathVariable UUID driveRoundId,
            @RequestParam(required = false) RoundStudentStatus status,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<RoundStudentResponse> list = roundService.listStudentsInRound(effectiveCollegeId, driveRoundId, status);
        return ResponseEntity.ok(ApiResponse.success(list, "Students in round fetched successfully"));
    }

    @PatchMapping("/drive-rounds/{driveRoundId}/students")
    public ResponseEntity<ApiResponse<List<RoundStudentResponse>>> bulkUpdateStudentsInRound(
            @PathVariable UUID driveRoundId,
            @Valid @RequestBody BulkUpdateRoundStudentsRequest request,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<RoundStudentResponse> list = roundService.bulkUpdateRoundStudents(effectiveCollegeId, driveRoundId, request);
        return ResponseEntity.ok(ApiResponse.success(list, "Student statuses in round updated successfully"));
    }

    @PostMapping("/drive-rounds/{driveRoundId}/advance")
    public ResponseEntity<ApiResponse<DriveRoundResponse>> advanceRound(@PathVariable UUID driveRoundId,
                                                                        @RequestParam(required = false) UUID collegeId,
                                                                        Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveRoundResponse response = roundService.advanceRound(effectiveCollegeId, driveRoundId);
        return ResponseEntity.ok(ApiResponse.success(response, "Selected candidates advanced to next round successfully"));
    }

    @PostMapping("/drive-rounds/{driveRoundId}/send-mail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendShortlistEmails(@PathVariable UUID driveRoundId,
                                                                                @RequestParam(required = false) UUID collegeId,
                                                                                Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        Map<String, Object> result = roundService.sendShortlistEmails(effectiveCollegeId, driveRoundId);
        return ResponseEntity.ok(ApiResponse.success(result, "Shortlist emails sent"));
    }
}
