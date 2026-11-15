package com.proveyu.college.web;

import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.application.StudentImportService;
import com.proveyu.college.application.StudentService;
import com.proveyu.college.application.TabularExporter;
import com.proveyu.college.application.dto.StudentDtos.*;
import com.proveyu.college.domain.CollegeEnums.DocType;
import com.proveyu.college.domain.CollegeEnums.StudentStatus;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "College - Students")
@RestController
@RequestMapping("/api/v1/college/students")
@RequiredArgsConstructor
public class CollegeStudentController {

    private final StudentService studentService;
    private final StudentImportService importService;
    private final TabularExporter exporter;
    private final CollegeFileService fileService;

    private static StudentFilter filter(String q, Integer batchYear, UUID branchId, BigDecimal minCgpa, BigDecimal maxCgpa, Integer maxBacklogs,
                                        StudentStatus status, Boolean placed, String skill, boolean deleted) {
        return new StudentFilter(q, batchYear, branchId, minCgpa, maxCgpa, maxBacklogs, status, placed, skill, deleted);
    }

    @Operation(summary = "Search students (server-side pagination, sorting and filters)")
    @GetMapping
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<StudentResponse>> list(CollegeContext ctx,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer batchYear,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) BigDecimal minCgpa,
            @RequestParam(required = false) BigDecimal maxCgpa, @RequestParam(required = false) Integer maxBacklogs,
            @RequestParam(required = false) StudentStatus status, @RequestParam(required = false) Boolean placed,
            @RequestParam(required = false) String skill, @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "rollNumber") String sort, @RequestParam(defaultValue = "asc") String dir) {
        return ApiResponse.success(studentService.page(ctx, filter(q, batchYear, branchId, minCgpa, maxCgpa, maxBacklogs, status, placed, skill, deleted),
                page, size, sort, dir));
    }

    @Operation(summary = "Export students with the same filters (format = xlsx | csv | pdf)")
    @GetMapping("/export")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> export(CollegeContext ctx, @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer batchYear,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) BigDecimal minCgpa,
            @RequestParam(required = false) BigDecimal maxCgpa, @RequestParam(required = false) Integer maxBacklogs,
            @RequestParam(required = false) StudentStatus status, @RequestParam(required = false) Boolean placed,
            @RequestParam(required = false) String skill) {
        var rows = studentService.all(ctx, filter(q, batchYear, branchId, minCgpa, maxCgpa, maxBacklogs, status, placed, skill, false));
        List<List<Object>> data = new ArrayList<>();
        rows.forEach(s -> data.add(List.of(s.rollNumber(), s.fullName(), s.email(), nz(s.phone()), nz(s.branchCode()), s.batchYear(),
                s.cgpa() == null ? "" : s.cgpa(), s.activeBacklogs(), s.status().name(), s.highestCtcLpa() == null ? "" : s.highestCtcLpa(),
                String.join("; ", s.skills()))));
        return exporter.export(new TabularExporter.Table("Students", List.of("Roll No", "Name", "Email", "Phone", "Branch", "Batch",
                "CGPA", "Backlogs", "Status", "Highest CTC (LPA)", "Skills"), data), format, "students").toResponse();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @GetMapping("/{id}")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<StudentDetail> get(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(studentService.detail(ctx, id));
    }

    @PostMapping
    @PreAuthorize(CollegeAccess.WRITE)
    public ResponseEntity<ApiResponse<StudentResponse>> create(CollegeContext ctx, @Valid @RequestBody StudentRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(studentService.create(ctx, request), "Student added"));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<StudentResponse> update(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody StudentRequest request) {
        return ApiResponse.success(studentService.update(ctx, id, request), "Student updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> delete(CollegeContext ctx, @PathVariable UUID id) {
        studentService.softDelete(ctx, id);
        return ApiResponse.success(null, "Student moved to trash");
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<StudentResponse> restore(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(studentService.restore(ctx, id), "Student restored");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<StudentResponse> status(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        return ApiResponse.success(studentService.changeStatus(ctx, id, request), "Status updated");
    }

    // ---------------------------------------------------------------- documents

    @GetMapping("/{id}/documents")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<DocumentResponse>> documents(CollegeContext ctx, @PathVariable UUID id) {
        studentService.scoped(ctx, id);
        return ApiResponse.success(studentService.documents(ctx, id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DocumentResponse> upload(CollegeContext ctx, @PathVariable UUID id, @RequestParam DocType docType,
                                                @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(studentService.uploadDocument(ctx, id, docType, file), "Document uploaded");
    }

    @PatchMapping("/documents/{docId}/verify")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DocumentResponse> verify(CollegeContext ctx, @PathVariable UUID docId, @Valid @RequestBody VerifyDocumentRequest request) {
        return ApiResponse.success(studentService.verifyDocument(ctx, docId, request), "Document updated");
    }

    @DeleteMapping("/documents/{docId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> deleteDocument(CollegeContext ctx, @PathVariable UUID docId) {
        studentService.deleteDocument(ctx, docId);
        return ApiResponse.success(null, "Document deleted");
    }

    @GetMapping("/documents/{docId}/file")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<Resource> documentFile(CollegeContext ctx, @PathVariable UUID docId) {
        CollegeFile f = studentService.documentFile(ctx, docId);
        return Downloads.inline(fileService.load(f), f);
    }

    // ---------------------------------------------------------------- import

    @Operation(summary = "Download the student import template (.xlsx)")
    @GetMapping("/import/template")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> template(CollegeContext ctx) {
        return new TabularExporter.Export(importService.template(ctx), "student-import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toResponse();
    }

    @Operation(summary = "Import students from .xlsx/.csv. dryRun=true validates and previews without saving.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<StudentImportService.ImportResult> importFile(CollegeContext ctx, @RequestPart("file") MultipartFile file,
                                                                      @RequestParam(defaultValue = "true") boolean dryRun) {
        var result = importService.importFile(ctx, file, dryRun);
        return ApiResponse.success(result, dryRun ? "Validation complete" : "Import complete");
    }

    @GetMapping("/import/history")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<StudentImportService.ImportHistory>> history(CollegeContext ctx,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(importService.history(ctx, page, size));
    }
}
