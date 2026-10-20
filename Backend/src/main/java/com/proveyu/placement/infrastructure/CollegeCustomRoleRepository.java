package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.CollegeCustomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollegeCustomRoleRepository extends JpaRepository<CollegeCustomRole, UUID> {

    List<CollegeCustomRole> findByOrganizationId(UUID organizationId);

    Optional<CollegeCustomRole> findByOrganizationIdAndRoleName(UUID organizationId, String roleName);

    boolean existsByOrganizationIdAndRoleName(UUID organizationId, String roleName);
}
