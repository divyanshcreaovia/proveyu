package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TierRepository extends JpaRepository<Tier, UUID> {
    List<Tier> findByCollegeIdOrderByTierLevelAsc(UUID collegeId);
    Optional<Tier> findByCollegeIdAndTierLevel(UUID collegeId, int tierLevel);
    Optional<Tier> findByCollegeIdAndTierId(UUID collegeId, UUID tierId);
}
