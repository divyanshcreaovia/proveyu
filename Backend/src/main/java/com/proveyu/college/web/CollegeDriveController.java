package com.proveyu.college.web;

import com.proveyu.college.application.*;
import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "College - Drives, eligibility, applications & rounds")
@RestController
@RequestMapping("/api/v1/college")
@RequiredArgsConstructor
public class CollegeDriveController {

    private final DriveService driveService;
    private final EligibilityService eligibilityService;
    private final ApplicationService applicationService;
    private final RoundService roundService;
    private final TabularExporter exporter;

    // ---------------------------------------------------------------- drives

    @GetMapping("/drives")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<DriveResponse>> list(CollegeContext ctx, @RequestParam(required = false) String q,
            @RequestParam(required = false) DriveStatus status, @RequestParam(required = false) DriveType type,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean deleted, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return ApiResponse.success(driveService.page(ctx, q, status, type, companyId, from, to, deleted, page, size, sort, dir));
    }

    @GetMapping("/drives/export")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> export(CollegeContext ctx, @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String q, @RequestParam(required = false) DriveStatus status, @RequestParam(required = false) DriveType type,
            @RequestParam(required = false) UUID companyId) {
        List<List<Object>> rows = new ArrayList<>();
        driveService.all(ctx, q, status, type, companyId, null, null).forEach(d -> rows.add(List.of(d.title(), n(d.companyName()), d.roleTitle(),
                d.driveType().name(), d.ctcLpa() == null ? "" : d.ctcLpa(), n(d.tierName()), d.driveDate() == null ? "" : d.driveDate().toString(),
                d.status().name(), d.applicationCounts().values().stream().mapToLong(Long::longValue).sum(), d.offersCount())));
        return exporter.export(new TabularExporter.Table("Placement drives", List.of("Drive", "Company", "Role", "Type", "CTC (LPA)", "Tier",
                "Date", "Status", "Applicants", "Offers"), rows), format, "drives").toResponse();
    }

    private static String n(String s) { return s == null ? "" : s; }

    @GetMapping("/drives/{id}")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<DriveResponse> get(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(driveService.get(ctx, id));
    }

    @PostMapping("/drives")
    @PreAuthorize(CollegeAccess.WRITE)
    public ResponseEntity<ApiResponse<DriveResponse>> create(CollegeContext ctx, @Valid @RequestBody DriveRequest r) {
        return ResponseEntity.status(201).body(ApiResponse.success(driveService.create(ctx, r), "Drive created as draft"));
    }

    @PutMapping("/drives/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DriveResponse> update(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody DriveRequest r) {
        return ApiResponse.success(driveService.update(ctx, id, r), "Drive updated");
    }

    @PostMapping("/drives/{id}/clone")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DriveResponse> cloneDrive(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(driveService.cloneDrive(ctx, id), "Drive cloned");
    }

    @Operation(summary = "Lifecycle: DRAFT -> PUBLISHED -> ONGOING -> COMPLETED, or CANCELLED")
    @PostMapping("/drives/{id}/status")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DriveResponse> transition(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody TransitionRequest r) {
        return ApiResponse.success(driveService.transition(ctx, id, r), "Drive is now " + r.status());
    }

    @DeleteMapping("/drives/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> delete(CollegeContext ctx, @PathVariable UUID id) {
        driveService.softDelete(ctx, id);
        return ApiResponse.success(null, "Drive moved to trash");
    }

    @PostMapping("/drives/{id}/restore")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<DriveResponse> restore(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(driveService.restore(ctx, id), "Drive restored");
    }

    // ---------------------------------------------------------------- eligibility

    @Operation(summary = "Preview eligible / ineligible students with reasons (no data is changed)")
    @GetMapping("/drives/{id}/eligibility")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<EligibilityPreview> eligibility(CollegeContext ctx, @PathVariable UUID id, @RequestParam(required = false) Boolean eligible,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(eligibilityService.preview(ctx, id, eligible, q, page, size));
    }

    @GetMapping("/drives/{id}/overrides")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<OverrideResponse>> overrides(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(eligibilityService.overrides(ctx, id));
    }

    @PostMapping("/drives/{id}/overrides")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<OverrideResponse> addOverride(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody OverrideRequest r) {
        return ApiResponse.success(eligibilityService.addOverride(ctx, id, r), "Override saved");
    }

    @DeleteMapping("/overrides/{overrideId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> removeOverride(CollegeContext ctx, @PathVariable UUID overrideId) {
        eligibilityService.removeOverride(ctx, overrideId);
        return ApiResponse.success(null, "Override removed");
    }

    // ---------------------------------------------------------------- applications

    @GetMapping("/drives/{id}/applications")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<ApplicationResponse>> applications(CollegeContext ctx, @PathVariable UUID id,
            @RequestParam(required = false) ApplicationStatus status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(applicationService.list(ctx, id, status, q, page, size));
    }

    @GetMapping("/drives/{id}/applications/export")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> exportApplications(CollegeContext ctx, @PathVariable UUID id, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<Object>> rows = new ArrayList<>();
        applicationService.list(ctx, id, null, null, 0, 100000).content().forEach(a -> rows.add(List.of(a.rollNumber(), a.studentName(), a.email(),
                n(a.branchCode()), a.batchYear(), a.cgpa() == null ? "" : a.cgpa(), a.status().name(), a.currentRound(), a.source().name())));
        return exporter.export(new TabularExporter.Table("Applicants", List.of("Roll No", "Name", "Email", "Branch", "Batch", "CGPA",
                "Status", "Round", "Source"), rows), format, "applicants").toResponse();
    }

    @PostMapping("/drives/{id}/applications")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<BulkResult> addApplicants(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody AddApplicantsRequest r) {
        return ApiResponse.success(applicationService.addApplicants(ctx, id, r), "Applicants registered");
    }

    @Operation(summary = "Bulk shortlist / waitlist / reject")
    @PatchMapping("/applications/status")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<BulkResult> bulkStatus(CollegeContext ctx, @Valid @RequestBody BulkStatusRequest r) {
        return ApiResponse.success(applicationService.bulkStatus(ctx, r), "Applications updated");
    }

    // ---------------------------------------------------------------- rounds

    @GetMapping("/drives/{id}/rounds")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<RoundResponse>> rounds(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(roundService.list(ctx, id));
    }

    @PostMapping("/drives/{id}/rounds")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RoundResponse> addRound(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody RoundRequest r) {
        return ApiResponse.success(roundService.add(ctx, id, r), "Round added");
    }

    @PutMapping("/drives/{id}/rounds/order")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<List<RoundResponse>> reorder(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody ReorderRequest r) {
        return ApiResponse.success(roundService.reorder(ctx, id, r), "Rounds reordered");
    }

    @PutMapping("/rounds/{roundId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RoundResponse> updateRound(CollegeContext ctx, @PathVariable UUID roundId, @Valid @RequestBody RoundRequest r) {
        return ApiResponse.success(roundService.update(ctx, roundId, r), "Round updated");
    }

    @DeleteMapping("/rounds/{roundId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> deleteRound(CollegeContext ctx, @PathVariable UUID roundId) {
        roundService.delete(ctx, roundId);
        return ApiResponse.success(null, "Round deleted");
    }

    @PostMapping("/rounds/{roundId}/start")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RoundResponse> start(CollegeContext ctx, @PathVariable UUID roundId) {
        return ApiResponse.success(roundService.start(ctx, roundId), "Round started");
    }

    @GetMapping("/rounds/{roundId}/results")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<RoundResultRow>> results(CollegeContext ctx, @PathVariable UUID roundId) {
        return ApiResponse.success(roundService.results(ctx, roundId));
    }

    @PutMapping("/rounds/{roundId}/results")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<List<RoundResultRow>> saveResults(CollegeContext ctx, @PathVariable UUID roundId, @Valid @RequestBody ResultsRequest r) {
        return ApiResponse.success(roundService.saveResults(ctx, roundId, r), "Results saved");
    }

    @Operation(summary = "Upload round results (.csv/.xlsx: roll_number, result, score, remarks)")
    @PostMapping(value = "/rounds/{roundId}/results/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RoundUploadResult> upload(CollegeContext ctx, @PathVariable UUID roundId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(roundService.upload(ctx, roundId, file), "Results uploaded");
    }

    @Operation(summary = "Complete the round and auto-advance PASS students")
    @PostMapping("/rounds/{roundId}/complete")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<RoundResponse> complete(CollegeContext ctx, @PathVariable UUID roundId) {
        return ApiResponse.success(roundService.complete(ctx, roundId), "Round completed");
    }
}
