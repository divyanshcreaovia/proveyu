package com.proveyu.booking.web;

import com.proveyu.booking.application.ReserveSlotUseCase;
import com.proveyu.booking.domain.Booking;
import com.proveyu.booking.web.dto.BookingDto.*;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final ReserveSlotUseCase reserveSlotUseCase;

    public BookingController(ReserveSlotUseCase reserveSlotUseCase) {
        this.reserveSlotUseCase = reserveSlotUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> reserve(
            @Valid @RequestBody ReserveSlotRequest request,
            Authentication authentication) {

        UUID candidateId = UUID.fromString(authentication.getName());
        Booking booking = reserveSlotUseCase.execute(candidateId, request.getSlotId());

        BookingResponse response = BookingResponse.builder()
                .id(booking.getId())
                .candidateId(booking.getCandidateId())
                .slotId(booking.getSlotId())
                .status(booking.getStatus())
                .heldAt(booking.getHeldAt())
                .expiresAt(booking.getExpiresAt())
                .confirmedAt(booking.getConfirmedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Slot reserved successfully for 15 minutes"));
    }
}
