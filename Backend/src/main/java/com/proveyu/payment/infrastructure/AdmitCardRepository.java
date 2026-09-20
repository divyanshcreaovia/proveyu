package com.proveyu.payment.infrastructure;

import com.proveyu.payment.domain.AdmitCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdmitCardRepository extends JpaRepository<AdmitCard, UUID> {
    Optional<AdmitCard> findByBookingId(UUID bookingId);
}
