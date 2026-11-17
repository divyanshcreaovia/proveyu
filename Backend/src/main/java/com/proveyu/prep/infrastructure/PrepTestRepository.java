package com.proveyu.prep.infrastructure;

import com.proveyu.prep.domain.PrepTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrepTestRepository extends JpaRepository<PrepTest, UUID> {
    /** Platform tests (uploaded by the ProveYu admin). */
    List<PrepTest> findAllByCollegeIdIsNullOrderByCreatedAtDesc();

    List<PrepTest> findAllByCollegeIdOrderByCreatedAtDesc(UUID collegeId);

    Optional<PrepTest> findByIdAndCollegeId(UUID id, UUID collegeId);

    List<PrepTest> findAllByPublishedTrueOrderByCreatedAtDesc();

    List<PrepTest> findAllByPublishedTrueAndLevelOrderNotNullOrderByLevelOrderAsc();
}
