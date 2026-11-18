package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminUserService;
import com.proveyu.admin.domain.AdminModel.AdminRow;
import com.proveyu.admin.domain.AdminModel.CreateAdminRequest;
import com.proveyu.admin.domain.AdminModel.UserPage;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proveyu.admin.web.AdminSupport.adminId;

@Tag(name = "Admin - Users and admins")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUsersController {
    private final AdminUserService service;

    @GetMapping("/users")
    public ApiResponse<UserPage> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                       @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(service.search(q, role, active, page, size));
    }

    @PostMapping("/users/{id}/block")
    public ApiResponse<Void> block(Authentication auth, @PathVariable UUID id) {
        service.setActive(adminId(auth), id, false);
        return ApiResponse.success(null, "User blocked");
    }

    @PostMapping("/users/{id}/unblock")
    public ApiResponse<Void> unblock(Authentication auth, @PathVariable UUID id) {
        service.setActive(adminId(auth), id, true);
        return ApiResponse.success(null, "User unblocked");
    }

    @Operation(summary = "E-mail the user a code to set a new password (devOtp is only returned in development)")
    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(Authentication auth, @PathVariable UUID id) {
        String otp = service.sendPasswordReset(adminId(auth), id);
        return ApiResponse.success(otp == null ? Map.of() : Map.of("devOtp", otp), "Password reset code sent");
    }

    @GetMapping("/admins")
    public ApiResponse<List<AdminRow>> admins() {
        return ApiResponse.success(service.admins());
    }

    @Operation(summary = "Create another platform admin. There is no admin sign-up: only a signed-in admin can do this.")
    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<AdminRow>> create(Authentication auth, @Valid @RequestBody CreateAdminRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createAdmin(adminId(auth), r), "Admin account created"));
    }

    /** Older path of the same action, kept for scripts. */
    @PostMapping("/users/create-admin")
    public ResponseEntity<ApiResponse<AdminRow>> createLegacy(Authentication auth, @Valid @RequestBody CreateAdminRequest r) {
        return create(auth, r);
    }
}
