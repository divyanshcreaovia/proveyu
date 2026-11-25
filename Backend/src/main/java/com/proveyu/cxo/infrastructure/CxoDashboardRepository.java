package com.proveyu.cxo.infrastructure;

import com.proveyu.cxo.domain.CxoDashboard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant- and owner-scoped: callers pass the college and user from the signed-in CXO. */
public interface CxoDashboardRepository extends JpaRepository<CxoDashboard, UUID> {
    List<CxoDashboard> findAllByCollegeIdAndUserIdOrderByPinnedDescUpdatedAtDesc(UUID collegeId, UUID userId);

    Optional<CxoDashboard> findByIdAndCollegeIdAndUserId(UUID id, UUID collegeId, UUID userId);

    long countByCollegeIdAndUserId(UUID collegeId, UUID userId);
}
