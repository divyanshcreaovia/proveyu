package com.proveyu.assessment.infrastructure;

import com.proveyu.assessment.domain.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DomainRepository extends JpaRepository<Domain, UUID> {
    List<Domain> findByActiveTrueOrderByNameAsc();
    Optional<Domain> findByNameIgnoreCase(String name);
    Optional<Domain> findByCodeIgnoreCase(String code);
}
