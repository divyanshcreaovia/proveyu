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

    /** Row lock: one interview booking at a time per college, so two requests cannot take the same slot. */
    @org.springframework.data.jpa.repository.Query(value = "select college_id from college_profiles where college_id = ?1 for update", nativeQuery = true)
    java.util.List<String> lockRow(String collegeId);
}
