package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminDashboardService;
import com.proveyu.admin.domain.AdminModel.CollegeDetail;
import com.proveyu.admin.domain.AdminModel.Dashboard;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin - Dashboard")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService service;

    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.success(service.dashboard());
    }

    /** One college with its numbers and team (approve / reject / suspend stay on POST /api/v1/admin/colleges/{id}/decision). */
    @GetMapping("/colleges/{collegeId}")
    public ApiResponse<CollegeDetail> college(@PathVariable UUID collegeId) {
        return ApiResponse.success(service.college(collegeId));
    }
}
