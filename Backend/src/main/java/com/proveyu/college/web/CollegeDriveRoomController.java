package com.proveyu.college.web;

import com.proveyu.college.application.DriveRoomService;
import com.proveyu.college.application.dto.DriveRoomDtos.*;
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

/** TPO side of the Drive Room: who from the company may work on a drive, and the panel scorecards they leave. */
@Tag(name = "College - Drive Room (company access)")
@RestController
@RequestMapping("/api/v1/college")
@RequiredArgsConstructor
public class CollegeDriveRoomController {

    private final DriveRoomService service;

    @GetMapping("/drives/{driveId}/recruiters")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<RecruiterAccessResponse>> list(CollegeContext ctx, @PathVariable UUID driveId) {
        return ApiResponse.success(service.list(ctx, driveId));
    }

    @PostMapping("/drives/{driveId}/recruiters")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RecruiterAccessResponse> invite(CollegeContext ctx, @PathVariable UUID driveId, @Valid @RequestBody RecruiterInviteRequest body) {
        return ApiResponse.success(service.invite(ctx, driveId, body), "Invitation sent");
    }

    @PostMapping("/drives/{driveId}/recruiters/{accessId}/resend")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RecruiterAccessResponse> resend(CollegeContext ctx, @PathVariable UUID driveId, @PathVariable UUID accessId) {
        return ApiResponse.success(service.resend(ctx, driveId, accessId), "Invitation sent again");
    }

    @PatchMapping("/drives/{driveId}/recruiters/{accessId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RecruiterAccessResponse> update(CollegeContext ctx, @PathVariable UUID driveId, @PathVariable UUID accessId,
                                                       @RequestBody RecruiterAccessUpdate body) {
        return ApiResponse.success(service.update(ctx, driveId, accessId, body), "Access updated");
    }

    @PostMapping("/drives/{driveId}/recruiters/{accessId}/revoke")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RecruiterAccessResponse> revoke(CollegeContext ctx, @PathVariable UUID driveId, @PathVariable UUID accessId) {
        return ApiResponse.success(service.revoke(ctx, driveId, accessId), "Access revoked");
    }

    @GetMapping("/rounds/{roundId}/scorecards")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<ScorecardResponse>> scorecards(CollegeContext ctx, @PathVariable UUID roundId) {
        return ApiResponse.success(service.scorecardsForCollege(ctx, roundId));
    }
}
