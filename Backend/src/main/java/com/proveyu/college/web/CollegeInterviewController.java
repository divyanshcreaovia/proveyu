package com.proveyu.college.web;

import com.proveyu.college.application.CampusInterviewService;
import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "College - Interview scheduling")
@RestController
@RequestMapping("/api/v1/college/interviews")
@RequiredArgsConstructor
public class CollegeInterviewController {

    private final CampusInterviewService campusInterviewService;

    @GetMapping
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<SlotResponse>> calendar(CollegeContext ctx,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.success(campusInterviewService.calendar(ctx, from, to));
    }

    @GetMapping("/drive/{driveId}")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<SlotResponse>> forDrive(CollegeContext ctx, @PathVariable UUID driveId) {
        return ApiResponse.success(campusInterviewService.forDrive(ctx, driveId));
    }

    @PostMapping("/check")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<List<Conflict>> check(CollegeContext ctx, @Valid @RequestBody SlotRequest r) {
        return ApiResponse.success(campusInterviewService.conflicts(ctx.collegeId(), null, r.applicationId(), r.panelName(), r.room(), r.startsAt(), r.endsAt()));
    }

    @PostMapping
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<SlotResponse> schedule(CollegeContext ctx, @Valid @RequestBody SlotRequest r) {
        return ApiResponse.success(campusInterviewService.schedule(ctx, r), "Interview scheduled");
    }

    @PostMapping("/bulk")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Map<String, Object>> bulk(CollegeContext ctx, @Valid @RequestBody BulkSlotRequest r) {
        return ApiResponse.success(campusInterviewService.bulkSchedule(ctx, r), "Interviews scheduled");
    }

    @PutMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<SlotResponse> reschedule(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody SlotRequest r) {
        return ApiResponse.success(campusInterviewService.reschedule(ctx, id, r), "Interview updated");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<SlotResponse> status(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody SlotStatusRequest r) {
        return ApiResponse.success(campusInterviewService.setStatus(ctx, id, r.status()), "Interview updated");
    }

    @PostMapping("/{id}/remind")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<SlotResponse> remind(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(campusInterviewService.remind(ctx, id), "Reminder sent");
    }
}
