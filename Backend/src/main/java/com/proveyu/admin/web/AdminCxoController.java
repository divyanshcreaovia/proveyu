package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminCxoService;
import com.proveyu.cxo.domain.CxoModel.CreateCxoRequest;
import com.proveyu.cxo.domain.CxoModel.CxoUserRow;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.proveyu.admin.web.AdminSupport.adminId;

@Tag(name = "Admin - CXO accounts")
@RestController
@RequestMapping("/api/v1/admin/cxo-users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCxoController {
    private final AdminCxoService service;

    @GetMapping
    public ApiResponse<List<CxoUserRow>> list() {
        return ApiResponse.success(service.list(), "OK");
    }

    @Operation(summary = "Create a CXO (college leadership) account for an approved college. There is no CXO sign-up.")
    @PostMapping
    public ResponseEntity<ApiResponse<CxoUserRow>> create(Authentication auth, @Valid @RequestBody CreateCxoRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(adminId(auth), r), "CXO account created"));
    }

    @PostMapping("/{memberId}/revoke")
    public ApiResponse<Void> revoke(Authentication auth, @PathVariable UUID memberId) {
        service.setActive(adminId(auth), memberId, false);
        return ApiResponse.success(null, "Access revoked");
    }

    @PostMapping("/{memberId}/restore")
    public ApiResponse<Void> restore(Authentication auth, @PathVariable UUID memberId) {
        service.setActive(adminId(auth), memberId, true);
        return ApiResponse.success(null, "Access restored");
    }
}
