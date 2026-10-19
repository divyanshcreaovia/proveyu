package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriveRoleRepository extends JpaRepository<DriveRole, UUID> {
    List<DriveRole> findByDriveId(UUID driveId);
    void deleteByDriveId(UUID driveId);
}
