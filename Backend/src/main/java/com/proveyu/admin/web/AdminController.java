package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminService;
import com.proveyu.admin.web.dto.AdminDto.CreateAdminRequest;
import com.proveyu.admin.web.dto.AdminDto.CreateExamRequest;
import com.proveyu.assessment.domain.Exam;
import com.proveyu.auth.domain.User;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/exams")
    public ResponseEntity<ApiResponse<Exam>> createExam(@Valid @RequestBody CreateExamRequest request) {
        Exam exam = adminService.createExam(
                request.getSkillId(),
                request.getLevel(),
                request.getTitle(),
                request.getDurationMinutes(),
                request.getTotalMarks(),
                request.getPassingMarks(),
                request.getPriceCents(),
                request.getCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(exam, "Exam configured successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = adminService.getPlatformStats();
        return ResponseEntity.ok(ApiResponse.success(stats, "Platform statistics retrieved successfully"));
    }
}
