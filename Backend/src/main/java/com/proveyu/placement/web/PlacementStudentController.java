package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementStudentService;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement/students")
@RequiredArgsConstructor
public class PlacementStudentController {

    private final PlacementStudentService studentService;
    private final CollegeContextResolver contextResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<EligibleStudentResponse>> registerStudent(@Valid @RequestBody StudentAcademicRequest request,
                                                                                @RequestParam(required = false) UUID collegeId,
                                                                                Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        EligibleStudentResponse response = studentService.registerOrUpdateStudent(effectiveCollegeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Student registered/updated successfully"));
    }

    @PostMapping("/bulk-import")
    public ResponseEntity<ApiResponse<StudentImportResponse>> bulkImportStudents(@RequestBody List<StudentImportRow> rows,
                                                                                 @RequestParam(required = false) UUID collegeId,
                                                                                 Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        StudentImportResponse response = studentService.bulkImportStudents(effectiveCollegeId, rows);
        return ResponseEntity.ok(ApiResponse.success(response, "Bulk import completed"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EligibleStudentResponse>>> listStudents(@RequestParam(required = false) UUID collegeId,
                                                                                  Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<EligibleStudentResponse> list = studentService.listStudents(effectiveCollegeId);
        return ResponseEntity.ok(ApiResponse.success(list, "Students fetched successfully"));
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<ApiResponse<EligibleStudentResponse>> getStudent(@PathVariable UUID studentId,
                                                                           @RequestParam(required = false) UUID collegeId,
                                                                           Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        EligibleStudentResponse response = studentService.getStudent(effectiveCollegeId, studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Student retrieved successfully"));
    }
}
