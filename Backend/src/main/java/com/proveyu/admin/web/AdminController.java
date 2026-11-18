package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminService;
import com.proveyu.admin.web.dto.AdminDto.CreateAdminRequest;
import com.proveyu.auth.domain.User;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/users/create-admin")
    public ResponseEntity<ApiResponse<User>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        User admin = adminService.createAdminUser(
                request.getEmail(),
                request.getFullName(),
                request.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin, "Admin account created successfully"));
    }
}
