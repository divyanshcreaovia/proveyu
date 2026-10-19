package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementDriveService;
import com.proveyu.placement.domain.DriveStatus;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement/drives")
@RequiredArgsConstructor
public class PlacementDriveController {

    private final PlacementDriveService driveService;
    private final CollegeContextResolver contextResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<DriveResponse>> createDrive(@Valid @RequestBody CreateDriveRequest request,
                                                                  @RequestParam(required = false) UUID collegeId,
                                                                  Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveResponse response = driveService.createDrive(effectiveCollegeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Placement drive created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriveResponse>>> listDrives(
            @RequestParam(required = false) DriveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate driveDate,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<DriveResponse> list = driveService.listDrives(effectiveCollegeId, status, driveDate);
        return ResponseEntity.ok(ApiResponse.success(list, "Placement drives fetched successfully"));
    }

    @GetMapping("/{driveId}")
    public ResponseEntity<ApiResponse<DriveResponse>> getDrive(@PathVariable UUID driveId,
                                                               @RequestParam(required = false) UUID collegeId,
                                                               Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveResponse response = driveService.getDrive(effectiveCollegeId, driveId);
        return ResponseEntity.ok(ApiResponse.success(response, "Placement drive retrieved successfully"));
    }

    @PutMapping("/{driveId}")
    public ResponseEntity<ApiResponse<DriveResponse>> updateDrive(@PathVariable UUID driveId,
                                                                  @Valid @RequestBody UpdateDriveRequest request,
                                                                  @RequestParam(required = false) UUID collegeId,
                                                                  Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveResponse response = driveService.updateDrive(effectiveCollegeId, driveId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Placement drive updated successfully"));
    }

    @PostMapping("/{driveId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelDrive(@PathVariable UUID driveId,
                                                         @RequestParam(required = false) UUID collegeId,
                                                         Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        driveService.cancelDrive(effectiveCollegeId, driveId);
        return ResponseEntity.ok(ApiResponse.success(null, "Placement drive cancelled successfully"));
    }

    @GetMapping("/{driveId}/eligible-students")
    public ResponseEntity<ApiResponse<DriveEligiblePreviewResponse>> previewEligibleStudents(@PathVariable UUID driveId,
                                                                                             @RequestParam(required = false) UUID collegeId,
                                                                                             Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveEligiblePreviewResponse response = driveService.previewEligibleStudents(effectiveCollegeId, driveId);
        return ResponseEntity.ok(ApiResponse.success(response, "Eligible students preview fetched successfully"));
    }

    @PostMapping("/{driveId}/finalize")
    public ResponseEntity<ApiResponse<DriveEligiblePreviewResponse>> finalizeDrive(@PathVariable UUID driveId,
                                                                                   @RequestParam(required = false) UUID collegeId,
                                                                                   Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        DriveEligiblePreviewResponse response = driveService.finalizeDrive(effectiveCollegeId, driveId);
        return ResponseEntity.ok(ApiResponse.success(response, "Placement drive finalized. Eligible candidates frozen into Round 1 as Shortlisted."));
    }
}
