package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.CollegeTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollegeTeamMemberRepository extends JpaRepository<CollegeTeamMember, UUID>, JpaSpecificationExecutor<CollegeTeamMember> {

    List<CollegeTeamMember> findByOrganizationId(UUID organizationId);

    Optional<CollegeTeamMember> findByUserId(UUID userId);

    Optional<CollegeTeamMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<CollegeTeamMember> findByEmail(String email);

    List<CollegeTeamMember> findByOrganizationIdAndCustomRoleId(UUID organizationId, UUID customRoleId);

    List<CollegeTeamMember> findByOrganizationIdAndRoleName(UUID organizationId, String roleName);

    List<CollegeTeamMember> findByOrganizationIdAndAssignedBranch(UUID organizationId, String assignedBranch);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);
}
