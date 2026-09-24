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
}
