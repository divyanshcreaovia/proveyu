package com.proveyu.payment.web;

import com.proveyu.payment.application.AdmitCardGeneratorService;
import com.proveyu.payment.application.PaymentService;
import com.proveyu.payment.application.PaymentService.PaymentOrderResponse;
import com.proveyu.payment.domain.AdmitCard;
import com.proveyu.payment.infrastructure.AdmitCardRepository;
import com.proveyu.payment.web.dto.PaymentDto.CreatePaymentOrderRequest;
import com.proveyu.payment.web.dto.PaymentDto.VerifyPaymentRequest;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AdmitCardRepository admitCardRepository;
    private final AdmitCardGeneratorService admitCardGeneratorService;

    @PostMapping("/api/v1/payments/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createPaymentOrder(@Valid @RequestBody CreatePaymentOrderRequest request) {
        PaymentOrderResponse response = paymentService.createUpiPaymentOrder(request.getBookingId(), request.getAmountCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Payment order created successfully"));
    }

    @PostMapping("/api/v1/payments/verify")
    public ResponseEntity<ApiResponse<AdmitCard>> verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        AdmitCard admitCard = paymentService.verifyPaymentAndConfirmBooking(
                request.getOrderId(),
                request.getPaymentId(),
                request.getSignature()
        );
        return ResponseEntity.ok(ApiResponse.success(admitCard, "Payment verified and admit card issued successfully"));
    }

    @GetMapping("/api/v1/payments/admit-card/{bookingId}")
    public ResponseEntity<ApiResponse<AdmitCard>> getAdmitCard(@PathVariable UUID bookingId) {
        AdmitCard admitCard = admitCardRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new DomainException("Admit card not found for booking", HttpStatus.NOT_FOUND, "ADMIT_CARD_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success(admitCard, "Admit card retrieved successfully"));
    }

    @GetMapping("/api/v1/admit-cards/download/{filename:.+}")
    public ResponseEntity<Resource> downloadAdmitCardPdf(@PathVariable String filename) {
        Resource resource = admitCardGeneratorService.loadAdmitCardPdf(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
