package com.proveyu.student.prep.infrastructure;

import com.proveyu.student.prep.domain.PrepAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PrepAttemptRepository extends JpaRepository<PrepAttempt, UUID> {
    List<PrepAttempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    List<PrepAttempt> findAllByUserIdInOrderByStartedAtDesc(java.util.Collection<UUID> userIds);

    /** Row lock, so two switch reports arriving together get consecutive numbers. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PrepAttempt a where a.id = :id")
    java.util.Optional<PrepAttempt> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query("select a.testId, count(a) from PrepAttempt a group by a.testId")
    List<Object[]> countByTest();

    /** Per test: submitted attempts, distinct students, average score in percent. */
    @Query("select a.testId, count(a), count(distinct a.userId), avg(a.score * 100.0 / a.maxScore) from PrepAttempt a "
            + "where a.submittedAt is not null and a.maxScore > 0 and a.testId in :ids group by a.testId")
    List<Object[]> statsByTest(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
