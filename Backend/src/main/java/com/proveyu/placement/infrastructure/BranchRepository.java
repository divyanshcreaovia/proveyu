package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByCollegeId(UUID collegeId);
    Optional<Branch> findByCollegeIdAndBranchCode(UUID collegeId, String branchCode);
    Optional<Branch> findByCollegeIdAndBranchId(UUID collegeId, UUID branchId);
}
