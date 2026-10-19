package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriveRoundRepository extends JpaRepository<DriveRound, UUID> {
    List<DriveRound> findByDriveIdOrderByRoundNumberAsc(UUID driveId);
    Optional<DriveRound> findByDriveIdAndRoundNumber(UUID driveId, int roundNumber);
    Optional<DriveRound> findByDriveIdAndRoundId(UUID driveId, UUID roundId);
}
