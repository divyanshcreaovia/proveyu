package com.proveyu.verification.infrastructure;

import com.proveyu.verification.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByGstin(String gstin);
    boolean existsByGstin(String gstin);
}
