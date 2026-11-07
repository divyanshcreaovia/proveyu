package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.PlacementDrive1;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlacementDriveRepository1 extends JpaRepository<PlacementDrive1, UUID>, JpaSpecificationExecutor<PlacementDrive1> {

    List<PlacementDrive1> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<PlacementDrive1> findByOrganizationIdAndStatus(UUID organizationId, String status);

    long countByOrganizationId(UUID organizationId);
}
