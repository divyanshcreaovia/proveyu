package com.proveyu.college.web;

import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.application.SettingsService;
import com.proveyu.college.application.dto.SettingsDtos.*;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.security.AllowPendingCollege;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "College - Settings")
@RestController
@RequestMapping("/api/v1/college/settings")
@RequiredArgsConstructor
public class CollegeSettingsController {

    private final SettingsService settingsService;
    private final CollegeFileService fileService;

    @GetMapping("/profile")
    @PreAuthorize(CollegeAccess.READ)
    @AllowPendingCollege
    public ApiResponse<ProfileResponse> profile(CollegeContext ctx) {
        return ApiResponse.success(settingsService.profile(ctx));
    }

    @PutMapping("/profile")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<ProfileResponse> updateProfile(CollegeContext ctx, @Valid @RequestBody ProfileRequest r) {
        return ApiResponse.success(settingsService.updateProfile(ctx, r), "Profile saved");
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<ProfileResponse> logo(CollegeContext ctx, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(settingsService.uploadLogo(ctx, file), "Logo updated");
    }

    @GetMapping("/logo")
    @PreAuthorize(CollegeAccess.READ)
    @AllowPendingCollege
    public ResponseEntity<Resource> getLogo(CollegeContext ctx) {
        CollegeFile f = settingsService.logo(ctx.collegeId());
        return Downloads.inline(fileService.load(f), f);
    }

    @GetMapping("/policy")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PolicyDto> policy(CollegeContext ctx) {
        return ApiResponse.success(settingsService.policy(ctx));
    }

    @PutMapping("/policy")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<PolicyDto> updatePolicy(CollegeContext ctx, @Valid @RequestBody PolicyDto r) {
        return ApiResponse.success(settingsService.updatePolicy(ctx, r), "Placement policy saved");
    }

    @GetMapping("/branches")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<BranchResponse>> branches(CollegeContext ctx) {
        return ApiResponse.success(settingsService.branches(ctx));
    }

    @PostMapping("/branches")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<BranchResponse> addBranch(CollegeContext ctx, @Valid @RequestBody BranchRequest r) {
        return ApiResponse.success(settingsService.addBranch(ctx, r), "Branch added");
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<BranchResponse> updateBranch(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody BranchRequest r) {
        return ApiResponse.success(settingsService.updateBranch(ctx, id, r), "Branch updated");
    }

    @DeleteMapping("/branches/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<Void> deleteBranch(CollegeContext ctx, @PathVariable UUID id) {
        settingsService.deleteBranch(ctx, id);
        return ApiResponse.success(null, "Branch deleted");
    }

    @GetMapping("/batches")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<BatchResponse>> batches(CollegeContext ctx) {
        return ApiResponse.success(settingsService.batches(ctx));
    }

    @PostMapping("/batches")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<BatchResponse> addBatch(CollegeContext ctx, @Valid @RequestBody BatchRequest r) {
        return ApiResponse.success(settingsService.saveBatch(ctx, null, r), "Batch added");
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<BatchResponse> updateBatch(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody BatchRequest r) {
        return ApiResponse.success(settingsService.saveBatch(ctx, id, r), "Batch updated");
    }

    @DeleteMapping("/batches/{id}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<Void> deleteBatch(CollegeContext ctx, @PathVariable UUID id) {
        settingsService.deleteBatch(ctx, id);
        return ApiResponse.success(null, "Batch deleted");
    }

    @GetMapping("/tiers")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<List<TierDto>> tiers(CollegeContext ctx) {
        return ApiResponse.success(settingsService.tiers(ctx));
    }

    @PutMapping("/tiers")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<List<TierDto>> saveTiers(CollegeContext ctx, @Valid @RequestBody TiersRequest r) {
        return ApiResponse.success(settingsService.saveTiers(ctx, r), "Tiers saved");
    }

    @GetMapping("/email-templates")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<List<TemplateDto>> templates(CollegeContext ctx) {
        return ApiResponse.success(settingsService.templates(ctx));
    }

    @PutMapping("/email-templates/{key}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<TemplateDto> saveTemplate(CollegeContext ctx, @PathVariable String key, @Valid @RequestBody TemplateDto r) {
        return ApiResponse.success(settingsService.saveTemplate(ctx, key, r), "Template saved");
    }

    @DeleteMapping("/email-templates/{key}")
    @PreAuthorize(CollegeAccess.ADMIN)
    public ApiResponse<Void> resetTemplate(CollegeContext ctx, @PathVariable String key) {
        settingsService.resetTemplate(ctx, key);
        return ApiResponse.success(null, "Template reset to default");
    }
}
