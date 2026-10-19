package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.PackageTierRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackageTierRuleRepository extends JpaRepository<PackageTierRule, UUID> {
    List<PackageTierRule> findByCollegeId(UUID collegeId);
    List<PackageTierRule> findByCollegeIdOrderByMinPackageLpaAsc(UUID collegeId);
    Optional<PackageTierRule> findByCollegeIdAndRuleId(UUID collegeId, UUID ruleId);
}
