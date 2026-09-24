package com.proveyu.booking.web.dto;

import com.proveyu.booking.domain.BookingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

public class BookingDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReserveSlotRequest {
        @NotNull(message = "Slot ID is required")
        private UUID slotId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingResponse {
        private UUID id;
        private UUID candidateId;
        private UUID slotId;
        private BookingStatus status;
        private Instant heldAt;
        private Instant expiresAt;
        private Instant confirmedAt;
    }
}
