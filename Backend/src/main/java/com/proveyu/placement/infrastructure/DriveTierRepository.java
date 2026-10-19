package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveTier;
import com.proveyu.placement.domain.DriveTierId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriveTierRepository extends JpaRepository<DriveTier, DriveTierId> {
    List<DriveTier> findByIdDriveId(UUID driveId);
    void deleteByIdDriveId(UUID driveId);
}
