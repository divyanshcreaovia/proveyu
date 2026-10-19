package com.proveyu.placement.web;

import com.proveyu.placement.application.PlacementOfferService;
import com.proveyu.placement.web.dto.PlacementDtos.CreateOffersRequest;
import com.proveyu.placement.web.dto.PlacementDtos.PlacementOfferResponse;
import com.proveyu.placement.web.dto.PlacementDtos.UpdateOfferStatusRequest;
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
@RequestMapping("/api/v1/placement/offers")
@RequiredArgsConstructor
public class PlacementOfferController {

    private final PlacementOfferService offerService;
    private final CollegeContextResolver contextResolver;

    @PostMapping("/drives/{driveId}")
    public ResponseEntity<ApiResponse<List<PlacementOfferResponse>>> createOffersFromFinalRound(
            @PathVariable UUID driveId,
            @RequestBody(required = false) CreateOffersRequest request,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<PlacementOfferResponse> responses = offerService.createOffersFromFinalRound(effectiveCollegeId, driveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responses, "Placement offers generated successfully"));
    }

    @PatchMapping("/{offerId}/status")
    public ResponseEntity<ApiResponse<PlacementOfferResponse>> updateOfferStatus(
            @PathVariable UUID offerId,
            @Valid @RequestBody UpdateOfferStatusRequest request,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        PlacementOfferResponse response = offerService.updateOfferStatus(effectiveCollegeId, offerId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Offer status updated successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlacementOfferResponse>>> listOffers(
            @RequestParam(required = false) UUID driveId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID collegeId,
            Authentication authentication) {
        UUID effectiveCollegeId = contextResolver.resolveCollegeId(authentication, collegeId);
        List<PlacementOfferResponse> list = offerService.listOffers(effectiveCollegeId, driveId, studentId);
        return ResponseEntity.ok(ApiResponse.success(list, "Placement offers retrieved successfully"));
    }
}
