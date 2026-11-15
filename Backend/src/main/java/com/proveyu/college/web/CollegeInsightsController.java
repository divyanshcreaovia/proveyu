package com.proveyu.college.web;

import com.proveyu.college.application.*;
import com.proveyu.college.application.CommunicationService.*;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "College - Analytics, reports, communication, audit & search")
@RestController
@RequestMapping("/api/v1/college")
@RequiredArgsConstructor
public class CollegeInsightsController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;
    private final CommunicationService communicationService;
    private final AuditService auditService;

    @GetMapping("/analytics/dashboard")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<AnalyticsService.Dashboard> dashboard(CollegeContext ctx, @RequestParam(required = false) Integer batchYear,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.dashboard(ctx, batchYear, from, to));
    }

    @GetMapping("/analytics/skill-gap")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<AnalyticsService.SkillGapRow>> skillGap(CollegeContext ctx, @RequestParam(required = false) Integer batchYear) {
        return ApiResponse.success(analyticsService.skillGap(ctx, batchYear));
    }

    @GetMapping("/reports/types")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<ReportService.ReportType>> reportTypes() {
        return ApiResponse.success(reportService.types());
    }

    @Operation(summary = "Preview a report as rows (JSON)")
    @GetMapping("/reports/{type}/preview")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<TabularExporter.Table> preview(CollegeContext ctx, @PathVariable String type, @RequestParam(required = false) Integer batchYear,
                                                      @RequestParam(required = false) UUID driveId) {
        return ApiResponse.success(reportService.build(ctx, type, batchYear, driveId));
    }

    @Operation(summary = "Download a report (format = xlsx | csv | pdf)")
    @GetMapping("/reports/{type}")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> report(CollegeContext ctx, @PathVariable String type, @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) Integer batchYear, @RequestParam(required = false) UUID driveId) {
        return reportService.export(ctx, type, format, batchYear, driveId).toResponse();
    }

    @GetMapping("/report-schedules")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<ReportService.ScheduleResponse>> schedules(CollegeContext ctx) {
        return ApiResponse.success(reportService.schedules(ctx));
    }

    @PostMapping("/report-schedules")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<ReportService.ScheduleResponse> schedule(CollegeContext ctx, @Valid @RequestBody ReportService.ScheduleRequest r) {
        return ApiResponse.success(reportService.createSchedule(ctx, r), "Report scheduled");
    }

    @PatchMapping("/report-schedules/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<ReportService.ScheduleResponse> toggle(CollegeContext ctx, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return ApiResponse.success(reportService.toggleSchedule(ctx, id, Boolean.TRUE.equals(body.get("active"))));
    }

    @DeleteMapping("/report-schedules/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> deleteSchedule(CollegeContext ctx, @PathVariable UUID id) {
        reportService.deleteSchedule(ctx, id);
        return ApiResponse.success(null, "Schedule deleted");
    }

    // ---------------------------------------------------------------- communication

    @PostMapping("/announcements/audience")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Map<String, Integer>> audience(CollegeContext ctx, @RequestBody AudienceFilter filter) {
        return ApiResponse.success(Map.of("count", communicationService.audience(ctx, filter).size()));
    }

    @PostMapping("/announcements")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<AnnouncementResponse> announce(CollegeContext ctx, @Valid @RequestBody AnnouncementRequest r) {
        AnnouncementResponse a = communicationService.announce(ctx, r);
        return ApiResponse.success(a, "Announcement sent to " + a.recipientCount() + " student(s)");
    }

    @GetMapping("/announcements")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<AnnouncementResponse>> announcements(CollegeContext ctx, @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(communicationService.announcements(ctx, page, size));
    }

    @GetMapping("/chat/contacts")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<ChatContact>> chatContacts(CollegeContext ctx, @RequestParam(required = false) String q) {
        return ApiResponse.success(communicationService.chatContacts(ctx, q));
    }

    @GetMapping("/search")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<SearchResult> search(CollegeContext ctx, @RequestParam String q) {
        return ApiResponse.success(communicationService.search(ctx, q));
    }

    // ---------------------------------------------------------------- audit

    @GetMapping("/audit-logs")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<PageResponse<AuditService.AuditEntry>> audit(CollegeContext ctx, @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(auditService.search(ctx, entityType, action, q, from, to, page, size));
    }
}
