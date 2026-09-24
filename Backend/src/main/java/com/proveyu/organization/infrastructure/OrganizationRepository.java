package com.proveyu.organization.infrastructure;

import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.domain.OrganizationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByUserId(UUID userId);
}
