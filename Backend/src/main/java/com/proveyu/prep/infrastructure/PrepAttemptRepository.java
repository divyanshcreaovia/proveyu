package com.proveyu.prep.infrastructure;

import com.proveyu.prep.domain.PrepAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PrepAttemptRepository extends JpaRepository<PrepAttempt, UUID> {
    List<PrepAttempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("select a.testId, count(a) from PrepAttempt a group by a.testId")
    List<Object[]> countByTest();
}
