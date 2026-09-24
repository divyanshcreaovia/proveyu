package com.proveyu.payment.web;

import com.proveyu.payment.application.RazorpayWebhookService;
import com.proveyu.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final RazorpayWebhookService razorpayWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("[RAZORPAY WEBHOOK CONTROLLER] Received webhook request. Signature present=[{}]", signature != null);

        boolean isSignatureValid = razorpayWebhookService.verifyWebhookSignature(payload, signature);
        if (!isSignatureValid) {
            log.warn("[RAZORPAY WEBHOOK INVALID] Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid Webhook Signature", "INVALID_WEBHOOK_SIGNATURE"));
        }

        razorpayWebhookService.processWebhookEvent(payload);

        return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully", "WEBHOOK_PROCESSED"));
    }
}
