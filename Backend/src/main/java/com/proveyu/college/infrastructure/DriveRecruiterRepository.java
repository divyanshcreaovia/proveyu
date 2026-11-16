package com.proveyu.college.infrastructure;

import com.proveyu.college.domain.DriveRecruiter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriveRecruiterRepository extends JpaRepository<DriveRecruiter, UUID> {
    Optional<DriveRecruiter> findByIdAndCollegeId(UUID id, UUID collegeId);

    List<DriveRecruiter> findAllByCollegeIdAndDriveIdOrderByCreatedAtAsc(UUID collegeId, UUID driveId);

    Optional<DriveRecruiter> findByDriveIdAndEmailIgnoreCase(UUID driveId, String email);

    Optional<DriveRecruiter> findByTokenHash(String tokenHash);

    List<DriveRecruiter> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, DriveRecruiter.Status status);

    Optional<DriveRecruiter> findByDriveIdAndUserIdAndStatus(UUID driveId, UUID userId, DriveRecruiter.Status status);
}
