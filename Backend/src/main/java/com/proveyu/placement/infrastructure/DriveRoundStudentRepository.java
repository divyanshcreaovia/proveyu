package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.DriveRoundStudent;
import com.proveyu.placement.domain.RoundStudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriveRoundStudentRepository extends JpaRepository<DriveRoundStudent, UUID> {
    List<DriveRoundStudent> findByDriveRoundId(UUID driveRoundId);
    List<DriveRoundStudent> findByDriveRoundIdAndStatus(UUID driveRoundId, RoundStudentStatus status);
    Optional<DriveRoundStudent> findByDriveRoundIdAndStudentId(UUID driveRoundId, UUID studentId);
    boolean existsByDriveRoundIdAndStudentId(UUID driveRoundId, UUID studentId);
    long countByDriveRoundIdAndStatus(UUID driveRoundId, RoundStudentStatus status);
    long countByDriveRoundId(UUID driveRoundId);
    List<DriveRoundStudent> findByDriveRoundIdAndStatusAndMailSentAtIsNull(UUID driveRoundId, RoundStudentStatus status);
    List<DriveRoundStudent> findByStudentId(UUID studentId);
}
