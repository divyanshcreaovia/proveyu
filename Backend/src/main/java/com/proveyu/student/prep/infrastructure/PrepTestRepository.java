package com.proveyu.student.prep.infrastructure;

import com.proveyu.student.prep.domain.PrepTest;
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

    /** Published exams whose window has started and whose students were not told yet. */
    List<PrepTest> findAllByModeAndPublishedTrueAndLiveNotifiedAtIsNullAndStartsAtLessThanEqualAndEndsAtGreaterThan(String mode, java.time.Instant start, java.time.Instant end);
}
