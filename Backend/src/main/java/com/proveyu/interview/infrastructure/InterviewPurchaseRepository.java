package com.proveyu.interview.infrastructure;

import com.proveyu.interview.domain.InterviewModel;
import com.proveyu.interview.domain.InterviewPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewPurchaseRepository extends JpaRepository<InterviewPurchase, UUID> {

    /** Row lock: submitting the UTR and the admin's approve / reject never act on the same purchase at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InterviewPurchase p where p.id = :id")
    Optional<InterviewPurchase> lockById(@Param("id") UUID id);

    long countByUserIdAndStatus(UUID userId, InterviewModel.PurchaseStatus status);

    List<InterviewPurchase> findTop10ByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, Collection<InterviewModel.PurchaseStatus> statuses);

    List<InterviewPurchase> findAllByStatusOrderBySubmittedAtAsc(InterviewModel.PurchaseStatus status);

    List<InterviewPurchase> findTop100ByStatusOrderByReviewedAtDesc(InterviewModel.PurchaseStatus status);
}
