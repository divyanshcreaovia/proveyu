package com.proveyu.admin.web;

import com.proveyu.admin.domain.AdminModel.SiteBanner;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.settings.PlatformSettings;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public website content managed by the platform admin (no sign-in). */
@Tag(name = "Public - Website")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicSiteController {
    private final PlatformSettings settings;

    @GetMapping("/banner")
    public ApiResponse<SiteBanner> banner() {
        return ApiResponse.success(new SiteBanner(settings.flag(PlatformSettings.BANNER_ENABLED), settings.text(PlatformSettings.BANNER_TEXT),
                settings.text(PlatformSettings.BANNER_LINK)));
    }
}
