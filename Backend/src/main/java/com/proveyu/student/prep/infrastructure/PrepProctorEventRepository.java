package com.proveyu.student.prep.infrastructure;

import com.proveyu.student.prep.domain.PrepProctorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PrepProctorEventRepository extends JpaRepository<PrepProctorEvent, UUID> {
    List<PrepProctorEvent> findAllByAttemptIdOrderBySeqAsc(UUID attemptId);

    /** Screenshots older than the retention period. */
    List<PrepProctorEvent> findAllByScreenshotPathNotNullAndOccurredAtBefore(java.time.Instant cutoff);

    /** Attempts with at least one switch, newest switch first. */
    @Query("select e.attemptId from PrepProctorEvent e group by e.attemptId order by max(e.occurredAt) desc")
    List<UUID> flaggedAttemptIds(org.springframework.data.domain.Pageable page);

    /** Per attempt: number of switches. */
    @Query("select e.attemptId, count(e) from PrepProctorEvent e where e.attemptId in :ids group by e.attemptId")
    List<Object[]> countByAttempt(@Param("ids") Collection<UUID> ids);
}
