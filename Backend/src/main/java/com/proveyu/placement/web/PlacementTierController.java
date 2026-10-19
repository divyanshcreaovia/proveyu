package com.proveyu.placement.web;

import com.proveyu.placement.application.PackageTierRuleService;
import com.proveyu.placement.application.PlacementTierService;
import com.proveyu.placement.web.dto.PlacementDtos.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class PlacementTierController {

    private final PlacementTierService tierService;
    private final PackageTierRuleService ruleService;
    private final CollegeContextResolver contextResolver;

    // --- Tier Endpoints ---

    @PostMapping("/tiers")
    public ResponseEntity<ApiResponse<TierResponse>> createTier(@Valid @RequestBody TierRequest request,
                                                                @RequestParam(required = false) UUID collegeId,
                                                                Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        TierResponse response = tierService.createTier(effectiveCollegeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Tier created successfully"));
    }

    @GetMapping("/tiers")
    public ResponseEntity<ApiResponse<List<TierResponse>>> listTiers(@RequestParam(required = false) UUID collegeId,
                                                                    Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<TierResponse> list = tierService.listTiers(effectiveCollegeId);
        return ResponseEntity.ok(ApiResponse.success(list, "Tiers fetched successfully"));
    }

    @GetMapping("/tiers/{tierId}")
    public ResponseEntity<ApiResponse<TierResponse>> getTier(@PathVariable UUID tierId,
                                                             @RequestParam(required = false) UUID collegeId,
                                                             Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        TierResponse response = tierService.getTier(effectiveCollegeId, tierId);
        return ResponseEntity.ok(ApiResponse.success(response, "Tier retrieved successfully"));
    }

    @PutMapping("/tiers/{tierId}")
    public ResponseEntity<ApiResponse<TierResponse>> updateTier(@PathVariable UUID tierId,
                                                                @Valid @RequestBody TierRequest request,
                                                                @RequestParam(required = false) UUID collegeId,
                                                                Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        TierResponse response = tierService.updateTier(effectiveCollegeId, tierId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tier updated successfully"));
    }

    @DeleteMapping("/tiers/{tierId}")
    public ResponseEntity<ApiResponse<Void>> deleteTier(@PathVariable UUID tierId,
                                                        @RequestParam(required = false) UUID collegeId,
                                                        Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        tierService.deleteTier(effectiveCollegeId, tierId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tier deleted successfully"));
    }

    // --- Package Tier Rule Endpoints ---

    @PostMapping("/package-tier-rules")
    public ResponseEntity<ApiResponse<PackageTierRuleResponse>> createRule(@Valid @RequestBody PackageTierRuleRequest request,
                                                                           @RequestParam(required = false) UUID collegeId,
                                                                           Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        PackageTierRuleResponse response = ruleService.createRule(effectiveCollegeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Package tier rule created successfully"));
    }

    @GetMapping("/package-tier-rules")
    public ResponseEntity<ApiResponse<List<PackageTierRuleResponse>>> listRules(@RequestParam(required = false) UUID collegeId,
                                                                                Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<PackageTierRuleResponse> list = ruleService.listRules(effectiveCollegeId);
        return ResponseEntity.ok(ApiResponse.success(list, "Package tier rules fetched successfully"));
    }

    @DeleteMapping("/package-tier-rules/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID ruleId,
                                                        @RequestParam(required = false) UUID collegeId,
                                                        Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        ruleService.deleteRule(effectiveCollegeId, ruleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Package tier rule deleted successfully"));
    }
}
