package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.admin.domain.AdminModel.AuditPage;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Audit log")
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AdminAuditService service;

    @GetMapping
    public ApiResponse<AuditPage> list(@RequestParam(required = false) String action, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(service.list(action, page, size));
    }
}
