package com.proveyu.college.web;

import com.proveyu.college.application.TeamService;
import com.proveyu.college.application.dto.TeamDtos.*;
import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "College - Team & roles")
@RestController
@RequestMapping("/api/v1/college/team")
@RequiredArgsConstructor
public class CollegeTeamController {

    private final TeamService teamService;

    @GetMapping("/members")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<MemberResponse>> members(CollegeContext ctx) {
        return ApiResponse.success(teamService.members(ctx));
    }

    @GetMapping("/role-matrix")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<RoleMatrixRow>> matrix() {
        return ApiResponse.success(TeamService.MATRIX);
    }

    @GetMapping("/invites")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<List<InviteResponse>> invites(CollegeContext ctx) {
        return ApiResponse.success(teamService.invites(ctx));
    }

    @PostMapping("/invites")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<InviteResponse> invite(CollegeContext ctx, @Valid @RequestBody InviteRequest r) {
        return ApiResponse.success(teamService.invite(ctx, r), "Invitation sent");
    }

    @PostMapping("/invites/{id}/resend")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<InviteResponse> resend(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(teamService.resend(ctx, id), "Invitation re-sent");
    }

    @DeleteMapping("/invites/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<Void> revokeInvite(CollegeContext ctx, @PathVariable UUID id) {
        teamService.revokeInvite(ctx, id);
        return ApiResponse.success(null, "Invitation revoked");
    }

    @PutMapping("/members/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<MemberResponse> update(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody MemberUpdateRequest r) {
        return ApiResponse.success(teamService.update(ctx, id, r), "Member updated");
    }

    @PostMapping("/members/{id}/revoke")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<MemberResponse> revoke(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(teamService.setStatus(ctx, id, MemberStatus.REVOKED), "Access revoked");
    }

    @PostMapping("/members/{id}/restore")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<MemberResponse> restore(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(teamService.setStatus(ctx, id, MemberStatus.ACTIVE), "Access restored");
    }
}
