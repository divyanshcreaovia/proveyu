package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminBroadcastService;
import com.proveyu.admin.domain.AdminModel.Audience;
import com.proveyu.admin.domain.AdminModel.BroadcastRequest;
import com.proveyu.admin.domain.AdminModel.BroadcastResult;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static com.proveyu.admin.web.AdminSupport.adminId;

@Tag(name = "Admin - Broadcast")
@RestController
@RequestMapping("/api/v1/admin/broadcast")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBroadcastController {
    private final AdminBroadcastService service;

    /** How many users the message would reach. */
    @GetMapping("/count")
    public ApiResponse<Map<String, Integer>> count(@RequestParam Audience audience, @RequestParam(required = false) UUID collegeId) {
        return ApiResponse.success(Map.of("recipients", service.count(audience, collegeId)));
    }

    @PostMapping
    public ApiResponse<BroadcastResult> send(Authentication auth, @Valid @RequestBody BroadcastRequest r) {
        BroadcastResult res = service.send(adminId(auth), r);
        return ApiResponse.success(res, "Sent to " + res.recipients() + " user(s)");
    }
}
