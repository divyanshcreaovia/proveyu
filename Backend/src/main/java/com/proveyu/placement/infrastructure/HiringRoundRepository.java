package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.HiringRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HiringRoundRepository extends JpaRepository<HiringRound, UUID> {
    Optional<HiringRound> findByRoundName(String roundName);
}
