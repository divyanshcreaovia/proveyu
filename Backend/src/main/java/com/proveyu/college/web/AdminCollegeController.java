package com.proveyu.college.web;

import com.proveyu.college.application.CollegeAdminService;
import com.proveyu.college.application.CollegeAdminService.CollegeSummary;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - College approvals")
@RestController
@RequestMapping("/api/v1/admin/colleges")
@RequiredArgsConstructor
public class AdminCollegeController {

    public record DecisionRequest(@NotNull ApprovalStatus status, @Size(max = 500) String note) {}

    private final CollegeAdminService adminService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<CollegeSummary>> list(@RequestParam(required = false) ApprovalStatus status) {
        return ApiResponse.success(adminService.list(status));
    }

    @PostMapping("/{collegeId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CollegeSummary> decide(Authentication auth, @PathVariable UUID collegeId, @Valid @RequestBody DecisionRequest r) {
        return ApiResponse.success(adminService.decide(UUID.fromString(auth.getName()), collegeId, r.status(), r.note()), "College " + r.status().name().toLowerCase());
    }
}
