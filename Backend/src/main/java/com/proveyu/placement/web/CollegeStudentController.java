package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementManagementService;
import com.proveyu.placement.application.dto.BulkPlacementRequestDto;
import com.proveyu.placement.application.dto.BulkUploadResultDto;
import com.proveyu.placement.application.dto.CollegeDashboardDto;
import com.proveyu.placement.application.dto.StudentProfileDto;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/college/students")
public class CollegeStudentController {

    private final PlacementManagementService placementManagementService;

    public CollegeStudentController(PlacementManagementService placementManagementService) {
        this.placementManagementService = placementManagementService;
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkUploadResultDto>> bulkUpload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        BulkUploadResultDto result = placementManagementService.bulkUploadStudents(currentUserId, file);
        return ResponseEntity.ok(ApiResponse.success(result, "Bulk student roster processed successfully"));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam(value = "sample", defaultValue = "true") boolean sample) {
        byte[] csvBytes = placementManagementService.downloadTemplate(sample);
        String fileName = sample ? "proveyu_student_import_sample_template.csv" : "proveyu_student_import_blank_template.csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StudentProfileDto>>> getStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "All") String branch,
            @RequestParam(required = false, defaultValue = "All") String tier,
            @RequestParam(required = false, defaultValue = "All") String status,
            @RequestParam(required = false) Double minCgpa,
            @RequestParam(required = false) Integer maxBacklogs,
            @RequestParam(required = false) Integer maxGapYears,
            @RequestParam(required = false) Double minTenth,
            @RequestParam(required = false) Double minTwelfth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        UUID currentUserId = UUID.fromString(authentication.getName());
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StudentProfileDto> result = placementManagementService.getStudents(
                currentUserId, search, branch, tier, status, minCgpa, maxBacklogs, maxGapYears, minTenth, minTwelfth, pageable
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Students fetched successfully"));
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<ApiResponse<StudentProfileDto>> getStudentById(
            @PathVariable UUID studentId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        StudentProfileDto dto = placementManagementService.getStudentById(currentUserId, studentId);
        return ResponseEntity.ok(ApiResponse.success(dto, "Student profile fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentProfileDto>> createStudent(
            @Valid @RequestBody StudentProfileDto dto,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        StudentProfileDto created = placementManagementService.createStudent(currentUserId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Student created successfully"));
    }

    @PutMapping("/{studentId}")
    public ResponseEntity<ApiResponse<StudentProfileDto>> updateStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentProfileDto dto,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        StudentProfileDto updated = placementManagementService.updateStudent(currentUserId, studentId, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Student profile updated successfully"));
    }

    @PostMapping("/bulk-placement")
    public ResponseEntity<ApiResponse<String>> bulkPlacement(
            @Valid @RequestBody BulkPlacementRequestDto dto,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        placementManagementService.bulkUpdatePlacement(currentUserId, dto);
        return ResponseEntity.ok(ApiResponse.success("Successfully updated placement status for selected candidates", "Placement Recorded"));
    }

    @GetMapping("/my-profile")
    public ResponseEntity<ApiResponse<StudentProfileDto>> getStudentSelfProfile(Authentication authentication) {
        UUID candidateUserId = UUID.fromString(authentication.getName());
        StudentProfileDto dto = placementManagementService.getStudentSelfProfile(candidateUserId);
        return ResponseEntity.ok(ApiResponse.success(dto, "Student self profile retrieved successfully"));
    }

    @PutMapping("/my-profile")
    public ResponseEntity<ApiResponse<StudentProfileDto>> updateStudentSelfProfile(
            @Valid @RequestBody StudentProfileDto dto,
            Authentication authentication) {
        UUID candidateUserId = UUID.fromString(authentication.getName());
        StudentProfileDto updated = placementManagementService.updateStudentSelfProfile(candidateUserId, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully"));
    }

    @GetMapping("/dashboard/kpi")
    public ResponseEntity<ApiResponse<CollegeDashboardDto>> getDashboardKpi(Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        CollegeDashboardDto kpi = placementManagementService.getDashboardKpi(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(kpi, "Dashboard KPIs fetched successfully"));
    }
}
