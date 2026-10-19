package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveBranch;
import com.proveyu.placement.domain.DriveBranchId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriveBranchRepository extends JpaRepository<DriveBranch, DriveBranchId> {
    List<DriveBranch> findByIdDriveId(UUID driveId);
    void deleteByIdDriveId(UUID driveId);
}
