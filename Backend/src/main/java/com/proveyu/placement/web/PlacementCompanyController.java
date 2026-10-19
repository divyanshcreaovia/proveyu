package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementCompanyService;
import com.proveyu.placement.web.dto.PlacementDtos.CompanyRequest;
import com.proveyu.placement.web.dto.PlacementDtos.CompanyResponse;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement/companies")
@RequiredArgsConstructor
public class PlacementCompanyController {

    private final PlacementCompanyService companyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(@Valid @RequestBody CompanyRequest request) {
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Company created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        List<CompanyResponse> list = companyService.listCompanies();
        return ResponseEntity.ok(ApiResponse.success(list, "Companies fetched successfully"));
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID companyId) {
        CompanyResponse response = companyService.getCompany(companyId);
        return ResponseEntity.ok(ApiResponse.success(response, "Company retrieved successfully"));
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(@PathVariable UUID companyId,
                                                                      @Valid @RequestBody CompanyRequest request) {
        CompanyResponse response = companyService.updateCompany(companyId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Company updated successfully"));
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(@PathVariable UUID companyId) {
        companyService.deleteCompany(companyId);
        return ResponseEntity.ok(ApiResponse.success(null, "Company deleted successfully"));
    }
}
