package com.proveyu.payment.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class PaymentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePaymentOrderRequest {
        @NotNull(message = "Booking ID is required")
        private UUID bookingId;

        @Min(value = 100, message = "Amount must be at least 100 paise / 1 INR")
        @Builder.Default
        private int amountCents = 149900;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyPaymentRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        private String paymentId;

        @NotBlank(message = "Signature is required")
        private String signature;
    }
}
