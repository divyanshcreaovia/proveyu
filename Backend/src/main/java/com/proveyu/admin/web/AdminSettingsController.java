package com.proveyu.admin.web;

import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.settings.PlatformSettings;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.proveyu.admin.web.AdminSupport.adminId;

/** Prices, free interview days, UPI, mock test rules, screenshot retention and the website banner: applied at once, no restart. */
@Tag(name = "Admin - Settings")
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {
    private final PlatformSettings settings;
    private final AdminAuditService audit;

    @GetMapping
    public ApiResponse<List<PlatformSettings.Entry>> list() {
        return ApiResponse.success(settings.all());
    }

    @PutMapping
    public ApiResponse<List<PlatformSettings.Entry>> update(Authentication auth, @RequestBody Map<String, String> values) {
        List<String> changes = settings.update(values, adminId(auth));
        if (!changes.isEmpty()) audit.log(adminId(auth), "SETTINGS_CHANGED", "SETTINGS", null, String.join("; ", changes));
        return ApiResponse.success(settings.all(), changes.isEmpty() ? "Nothing changed" : "Settings saved");
    }

    @DeleteMapping("/{key}")
    public ApiResponse<List<PlatformSettings.Entry>> reset(Authentication auth, @PathVariable String key) {
        audit.log(adminId(auth), "SETTINGS_CHANGED", "SETTINGS", key, settings.reset(key));
        return ApiResponse.success(settings.all(), "Back to the default");
    }
}
