package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveStatus;
import com.proveyu.placement.domain.PlacementDrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, UUID> {
    List<PlacementDrive> findByCollegeId(UUID collegeId);
    Optional<PlacementDrive> findByDriveIdAndCollegeId(UUID driveId, UUID collegeId);
    List<PlacementDrive> findByCollegeIdAndStatus(UUID collegeId, DriveStatus status);
    List<PlacementDrive> findByCollegeIdAndDriveDate(UUID collegeId, LocalDate driveDate);
    List<PlacementDrive> findByCollegeIdAndStatusAndDriveDate(UUID collegeId, DriveStatus status, LocalDate driveDate);
}
