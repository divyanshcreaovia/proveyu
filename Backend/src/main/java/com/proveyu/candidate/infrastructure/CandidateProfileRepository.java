package com.proveyu.candidate.infrastructure;

import com.proveyu.candidate.domain.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByUserId(UUID userId);
    List<CandidateProfile> findByDomainId(UUID domainId);
}
