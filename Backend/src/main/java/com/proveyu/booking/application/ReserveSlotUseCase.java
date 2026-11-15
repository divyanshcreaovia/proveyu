package com.proveyu.booking.application;

import com.proveyu.booking.domain.Booking;
import com.proveyu.booking.domain.SlotInventory;
import com.proveyu.booking.infrastructure.BookingRepository;
import com.proveyu.booking.infrastructure.SlotInventoryRepository;
import com.proveyu.booking.infrastructure.locking.SlotLockService;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.SlotNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReserveSlotUseCase {

    private final SlotInventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final SlotLockService lockService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public Booking execute(UUID candidateId, UUID slotId) {
        Booking booking = lockService.withSlotLock(slotId, () -> {
            SlotInventory inventory = inventoryRepository.findByIdForUpdate(slotId)
                    .orElseThrow(SlotNotFoundException::new);

            inventory.reserveOneSeat();
            inventoryRepository.save(inventory);

            Booking bookingObj = Booking.hold(candidateId, slotId, Duration.ofMinutes(15));
            return bookingRepository.save(bookingObj);
        });

        // Trigger Async Slot Booking Email Notification
        // Use the real slot date / time / exam title (previously a hard-coded date was sent)
        var details = inventoryRepository.findSlotDetails(slotId.toString());
        userRepository.findById(candidateId).ifPresent(user -> details.ifPresent(d -> emailService.sendSlotBookingConfirmationEmail(
                user.getEmail(),
                user.getFullName(),
                d.getExamTitle(),
                formatSlot(d.getSlotDate(), d.getStartTime(), d.getEndTime())
        )));

        return booking;
    }

    static String formatSlot(java.time.LocalDate date, java.time.LocalTime start, java.time.LocalTime end) {
        java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH);
        java.time.format.DateTimeFormatter time = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH);
        return date.format(day) + " (" + start.format(time) + " - " + end.format(time) + " IST)";
    }
}
