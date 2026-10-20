package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.PlacementDrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, UUID>, JpaSpecificationExecutor<PlacementDrive> {

    List<PlacementDrive> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<PlacementDrive> findByOrganizationIdAndStatus(UUID organizationId, String status);

    long countByOrganizationId(UUID organizationId);
}
