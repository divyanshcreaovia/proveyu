package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementBranchService;
import com.proveyu.placement.web.dto.PlacementDtos.BranchRequest;
import com.proveyu.placement.web.dto.PlacementDtos.BranchResponse;
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
@RequestMapping("/api/v1/placement/branches")
@RequiredArgsConstructor
public class PlacementBranchController {

    private final PlacementBranchService branchService;
    private final CollegeContextResolver contextResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@Valid @RequestBody BranchRequest request,
                                                                    @RequestParam(required = false) UUID collegeId,
                                                                    Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        BranchResponse response = branchService.createBranch(effectiveCollegeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Branch created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> listBranches(@RequestParam(required = false) UUID collegeId,
                                                                          Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<BranchResponse> list = branchService.listBranches(effectiveCollegeId);
        return ResponseEntity.ok(ApiResponse.success(list, "Branches fetched successfully"));
    }

    @GetMapping("/{branchId}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable UUID branchId,
                                                                 @RequestParam(required = false) UUID collegeId,
                                                                 Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        BranchResponse response = branchService.getBranch(effectiveCollegeId, branchId);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch retrieved successfully"));
    }

    @DeleteMapping("/{branchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable UUID branchId,
                                                          @RequestParam(required = false) UUID collegeId,
                                                          Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        branchService.deleteBranch(effectiveCollegeId, branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Branch deleted successfully"));
    }
}
