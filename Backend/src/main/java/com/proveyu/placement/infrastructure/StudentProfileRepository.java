package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.StudentProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID>, JpaSpecificationExecutor<StudentProfile> {

    List<StudentProfile> findByOrganizationId(UUID organizationId);

    Page<StudentProfile> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<StudentProfile> findByOrganizationIdAndRollNumber(UUID organizationId, String rollNumber);

    Optional<StudentProfile> findByUserId(UUID userId);

    Optional<StudentProfile> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<StudentProfile> findByOrganizationIdAndBranch(UUID organizationId, String branch);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndPlacementStatus(UUID organizationId, String placementStatus);

    long countByOrganizationIdAndBranch(UUID organizationId, String branch);
}
