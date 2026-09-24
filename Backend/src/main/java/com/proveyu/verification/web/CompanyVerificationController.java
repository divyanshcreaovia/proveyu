package com.proveyu.verification.web;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.verification.application.GstVerificationService;
import com.proveyu.verification.domain.Company;
import com.proveyu.verification.web.dto.CompanyVerificationDto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recruiters/companies")
public class CompanyVerificationController {

    private final GstVerificationService gstVerificationService;

    public CompanyVerificationController(GstVerificationService gstVerificationService) {
        this.gstVerificationService = gstVerificationService;
    }

    @PostMapping("/verify-gst")
    public ResponseEntity<ApiResponse<Company>> verifyGst(@Valid @RequestBody VerifyGstRequest request) {
        Company company = gstVerificationService.verifyCompanyGst(
                request.getLegalName(),
                request.getTradeName(),
                request.getGstin(),
                request.getRegisteredAddress(),
                request.getState(),
                request.getWebsiteDomain()
        );
        return ResponseEntity.ok(ApiResponse.success(company, "Company GST verified successfully"));
    }
}
