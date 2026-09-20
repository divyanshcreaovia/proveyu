package com.proveyu.organization.infrastructure;

import com.proveyu.organization.domain.OrganizationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationCandidateRepository extends JpaRepository<OrganizationCandidate, UUID> {
    List<OrganizationCandidate> findByOrganizationId(UUID organizationId);
    Optional<OrganizationCandidate> findByOrganizationIdAndCandidateId(UUID organizationId, UUID candidateId);
}
