package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementDriveService1;
import com.proveyu.placement.application.dto.PlacementDriveDto;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/college/placements")
public class CollegePlacementController {

    private final PlacementDriveService1 driveService;

    public CollegePlacementController(PlacementDriveService1 driveService) {
        this.driveService = driveService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlacementDriveDto.Response>>> getDrives(Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        List<PlacementDriveDto.Response> drives = driveService.getDrives(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(drives, "Placement drives fetched successfully"));
    }

    @GetMapping("/{driveId}")
    public ResponseEntity<ApiResponse<PlacementDriveDto.Response>> getDriveById(
            @PathVariable UUID driveId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        PlacementDriveDto.Response drive = driveService.getDriveById(currentUserId, driveId);
        return ResponseEntity.ok(ApiResponse.success(drive, "Placement drive fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlacementDriveDto.Response>> createDrive(
            @Valid @RequestBody PlacementDriveDto.Request request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        PlacementDriveDto.Response created = driveService.createDrive(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Placement drive created successfully"));
    }

    @PutMapping("/{driveId}")
    public ResponseEntity<ApiResponse<PlacementDriveDto.Response>> updateDrive(
            @PathVariable UUID driveId,
            @Valid @RequestBody PlacementDriveDto.Request request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        PlacementDriveDto.Response updated = driveService.updateDrive(currentUserId, driveId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Placement drive updated successfully"));
    }

    @DeleteMapping("/{driveId}")
    public ResponseEntity<ApiResponse<String>> deleteDrive(
            @PathVariable UUID driveId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        driveService.deleteDrive(currentUserId, driveId);
        return ResponseEntity.ok(ApiResponse.success("Placement drive deleted successfully", "Drive Deleted"));
    }
}
