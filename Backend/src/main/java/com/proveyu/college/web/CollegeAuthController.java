package com.proveyu.college.web;

import com.proveyu.auth.web.dto.AuthDto.AuthResponse;
import com.proveyu.college.application.CollegeOnboardingService;
import com.proveyu.college.application.dto.TeamDtos.*;
import com.proveyu.college.security.AllowPendingCollege;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "College - Onboarding & session")
@RestController
@RequestMapping("/api/v1/college")
@RequiredArgsConstructor
public class CollegeAuthController {

    private final CollegeOnboardingService onboardingService;
    private final com.proveyu.college.application.DriveRoomService driveRoomService;

    @Operation(summary = "Look up a company Drive Room invitation (public)")
    @GetMapping("/auth/recruiter-invite/{token}")
    public ApiResponse<com.proveyu.college.application.dto.DriveRoomDtos.RecruiterInviteInfo> recruiterInvite(@PathVariable String token) {
        return ApiResponse.success(driveRoomService.inviteInfo(token));
    }

    @Operation(summary = "Accept a company Drive Room invitation (public): new e-mail creates a recruiter account, existing recruiter confirms with password")
    @PostMapping("/auth/recruiter-accept")
    public ApiResponse<com.proveyu.auth.web.dto.AuthDto.AuthResponse> recruiterAccept(
            @Valid @RequestBody com.proveyu.college.application.dto.DriveRoomDtos.RecruiterAcceptRequest request) {
        return ApiResponse.success(driveRoomService.accept(request), "Welcome to the Drive Room");
    }

    @Operation(summary = "Register a college (public). Creates the TPO account and sends an e-mail verification link.")
    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody CollegeRegisterRequest request) {
        RegisterResponse r = onboardingService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.success(r, r.message()));
    }

    @Operation(summary = "Look up a team invitation (public)")
    @GetMapping("/auth/invite/{token}")
    public ApiResponse<InviteInfo> invite(@PathVariable String token) {
        return ApiResponse.success(onboardingService.inviteInfo(token));
    }

    @Operation(summary = "Accept a team invitation and set a password (public). Returns a session.")
    @PostMapping("/auth/accept-invite")
    public ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptInviteRequest request) {
        return ApiResponse.success(onboardingService.acceptInvite(request), "Welcome aboard!");
    }

    @Operation(summary = "Current college staff context (works while the college is pending approval)")
    @GetMapping("/me")
    @PreAuthorize(CollegeAccess.READ)
    @AllowPendingCollege
    public ApiResponse<MeResponse> me(CollegeContext ctx) {
        return ApiResponse.success(onboardingService.me(ctx));
    }
}
