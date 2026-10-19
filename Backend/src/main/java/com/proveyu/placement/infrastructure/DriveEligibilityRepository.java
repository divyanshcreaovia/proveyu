package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriveEligibilityRepository extends JpaRepository<DriveEligibility, UUID> {
    Optional<DriveEligibility> findByDriveId(UUID driveId);
    void deleteByDriveId(UUID driveId);
}
