package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Export of AI interview payments (review and approval stay on /api/v1/admin/interview/purchases). */
@Tag(name = "Admin - Payments")
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentsController {
    private final AdminPaymentService service;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(defaultValue = "xlsx") String format) {
        return service.export(status, format).toResponse();
    }
}
