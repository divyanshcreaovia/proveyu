package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementManagementService.OrgUserContext;
import com.proveyu.placement.application.CollegeTeamService;
import com.proveyu.placement.application.dto.TeamMemberDto;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/college/team")
public class CollegeTeamController {

    private final CollegeTeamService teamService;

    public CollegeTeamController(CollegeTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/context")
    public ResponseEntity<ApiResponse<OrgUserContext>> getContext(Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        OrgUserContext context = teamService.getCurrentUserContext(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(context, "User context resolved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamMemberDto.Response>>> getTeamMembers(Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        List<TeamMemberDto.Response> members = teamService.getTeamMembers(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(members, "College team members fetched successfully"));
    }

    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<TeamMemberDto.Response>> inviteMember(
            @Valid @RequestBody TeamMemberDto.InviteRequest request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        TeamMemberDto.Response response = teamService.inviteOrAddTeamMember(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Team member invited successfully"));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ApiResponse<TeamMemberDto.Response>> updateMember(
            @PathVariable UUID memberId,
            @Valid @RequestBody TeamMemberDto.UpdateRequest request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        TeamMemberDto.Response response = teamService.updateTeamMember(currentUserId, memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Team member updated successfully"));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<String>> deleteMember(
            @PathVariable UUID memberId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        teamService.deleteTeamMember(currentUserId, memberId);
        return ResponseEntity.ok(ApiResponse.success("Team member removed successfully", "Member Deleted"));
    }
}
