package com.proveyu.student.prep.infrastructure;

import com.proveyu.student.prep.domain.PrepAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PrepAttemptRepository extends JpaRepository<PrepAttempt, UUID> {
    List<PrepAttempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("select a.testId, count(a) from PrepAttempt a group by a.testId")
    List<Object[]> countByTest();

    /** Per test: submitted attempts, distinct students, average score in percent. */
    @Query("select a.testId, count(a), count(distinct a.userId), avg(a.score * 100.0 / a.maxScore) from PrepAttempt a "
            + "where a.submittedAt is not null and a.maxScore > 0 and a.testId in :ids group by a.testId")
    List<Object[]> statsByTest(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
