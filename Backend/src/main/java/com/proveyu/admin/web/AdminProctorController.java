package com.proveyu.admin.web;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.student.prep.application.ProctorService;
import com.proveyu.student.prep.domain.PrepModel.AdminAttemptRow;
import com.proveyu.student.prep.domain.PrepModel.ProctorLog;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Mock test attempts in which a student left the test, across all colleges, with the screenshots. */
@Tag(name = "Admin - Proctoring")
@RestController
@RequestMapping("/api/v1/admin/proctoring")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProctorController {
    private final ProctorService service;

    @GetMapping
    public ApiResponse<List<AdminAttemptRow>> flagged() {
        return ApiResponse.success(service.adminFlagged());
    }

    @GetMapping("/attempts/{id}")
    public ApiResponse<ProctorLog> log(@PathVariable UUID id) {
        return ApiResponse.success(service.adminLog(id));
    }

    @GetMapping("/screenshots/{eventId}")
    public ResponseEntity<byte[]> screenshot(@PathVariable UUID eventId) {
        ProctorService.Screenshot s = service.adminScreenshot(eventId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(s.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff").body(s.bytes());
    }
}
