package com.proveyu.booking.infrastructure;

import com.proveyu.booking.domain.SlotInventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlotInventoryRepository extends JpaRepository<SlotInventory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "4000"))
    @Query("SELECT s FROM SlotInventory s WHERE s.slotId = :slotId")
    Optional<SlotInventory> findByIdForUpdate(@Param("slotId") UUID slotId);

    /** Slot calendar details used in booking e-mails. */
    interface SlotDetails {
        java.time.LocalDate getSlotDate();
        java.time.LocalTime getStartTime();
        java.time.LocalTime getEndTime();
        String getExamTitle();
    }

    @Query(value = "SELECT ds.slot_date AS slotDate, ds.start_time AS startTime, ds.end_time AS endTime, e.title AS examTitle "
            + "FROM date_slots ds JOIN exams e ON e.id = ds.exam_id WHERE ds.id = :slotId", nativeQuery = true)
    Optional<SlotDetails> findSlotDetails(@Param("slotId") String slotId);
}
