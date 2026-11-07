package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.PlacementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlacementRecordRepository extends JpaRepository<PlacementRecord, UUID> {

    List<PlacementRecord> findByOrganizationId(UUID organizationId);

    List<PlacementRecord> findByStudentProfileId(UUID studentProfileId);

    List<PlacementRecord> findByDriveId(UUID driveId);

    long countByOrganizationId(UUID organizationId);
}
