package com.proveyu.booking.infrastructure;

import com.proveyu.booking.domain.Booking;
import com.proveyu.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByCandidateId(UUID candidateId);

    @Query("SELECT b FROM Booking b WHERE b.status = 'HELD' AND b.expiresAt < :now")
    List<Booking> findExpiredHeldBookings(@Param("now") Instant now);
}
