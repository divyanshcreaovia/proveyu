package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("placementCompanyRepository")
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByName(String name);
    boolean existsByName(String name);
}
