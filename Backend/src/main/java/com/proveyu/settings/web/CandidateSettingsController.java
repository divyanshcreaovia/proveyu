package com.proveyu.settings.web;

import com.proveyu.auth.domain.User;
import com.proveyu.settings.application.CandidateSettingsService;
import com.proveyu.settings.application.dto.SettingsDto;
import com.proveyu.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates/settings")
@RequiredArgsConstructor
public class CandidateSettingsController {

    private final CandidateSettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SettingsDto>> getSettings(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        SettingsDto settings = settingsService.getSettings(userId);
        return ResponseEntity.ok(ApiResponse.success(settings, "Settings retrieved successfully"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SettingsDto>> updateSettings(Authentication authentication, @RequestBody SettingsDto dto) {
        UUID userId = UUID.fromString(authentication.getName());
        SettingsDto updated = settingsService.updateSettings(userId, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Settings updated successfully"));
    }
}
