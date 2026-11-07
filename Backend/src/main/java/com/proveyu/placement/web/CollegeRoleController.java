package com.proveyu.placement.web;

import com.proveyu.placement.application.CollegeTeamService;
import com.proveyu.placement.application.dto.CustomRoleDto;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/college/roles")
public class CollegeRoleController {

    private final CollegeTeamService teamService;

    public CollegeRoleController(CollegeTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomRoleDto.Response>>> getCustomRoles(Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        List<CustomRoleDto.Response> roles = teamService.getCustomRoles(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(roles, "Custom roles fetched successfully"));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<ApiResponse<CustomRoleDto.Response>> getCustomRoleById(
            @PathVariable UUID roleId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        CustomRoleDto.Response role = teamService.getCustomRoleById(currentUserId, roleId);
        return ResponseEntity.ok(ApiResponse.success(role, "Custom role fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomRoleDto.Response>> createCustomRole(
            @Valid @RequestBody CustomRoleDto.Request request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        CustomRoleDto.Response created = teamService.createCustomRole(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Custom role created successfully"));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResponse<CustomRoleDto.Response>> updateCustomRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody CustomRoleDto.Request request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        CustomRoleDto.Response updated = teamService.updateCustomRole(currentUserId, roleId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Custom role updated successfully"));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<String>> deleteCustomRole(
            @PathVariable UUID roleId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        teamService.deleteCustomRole(currentUserId, roleId);
        return ResponseEntity.ok(ApiResponse.success("Custom role deleted successfully", "Role Deleted"));
    }
}
