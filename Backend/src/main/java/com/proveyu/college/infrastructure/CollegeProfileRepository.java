package com.proveyu.college.infrastructure;

import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.college.domain.CollegeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollegeProfileRepository extends JpaRepository<CollegeProfile, UUID> {
    List<CollegeProfile> findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus status);
    List<CollegeProfile> findAllByOrderByCreatedAtDesc();
    boolean existsByAicteCodeIgnoreCase(String aicteCode);
}
