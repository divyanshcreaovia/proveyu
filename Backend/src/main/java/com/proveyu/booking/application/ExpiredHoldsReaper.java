package com.proveyu.booking.application;

import com.proveyu.booking.domain.Booking;
import com.proveyu.booking.infrastructure.BookingRepository;
import com.proveyu.booking.infrastructure.SlotInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class ExpiredHoldsReaper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredHoldsReaper.class);

    private final BookingRepository bookingRepository;
    private final SlotInventoryRepository inventoryRepository;

    public ExpiredHoldsReaper(BookingRepository bookingRepository, SlotInventoryRepository inventoryRepository) {
        this.bookingRepository = bookingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void sweepExpiredHolds() {
        Instant now = Instant.now();
        List<Booking> expiredBookings = bookingRepository.findExpiredHeldBookings(now);
        for (Booking booking : expiredBookings) {
            booking.expire();
            bookingRepository.save(booking);

            inventoryRepository.findById(booking.getSlotId()).ifPresent(inventory -> {
                inventory.releaseOneSeat();
                inventoryRepository.save(inventory);
            });
            log.info("Released expired slot hold for booking ID: {}", booking.getId());
        }
    }
}
