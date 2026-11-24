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

    /** Attempts with at least one proctoring event (switch or AI), newest event first. */
    @Query("select e.attemptId from PrepProctorEvent e group by e.attemptId order by max(e.occurredAt) desc")
    List<UUID> flaggedAttemptIds(org.springframework.data.domain.Pageable page);

    /** Per attempt: number of events of the given kinds (the tab-switch kinds for switches). */
    @Query("select e.attemptId, count(e) from PrepProctorEvent e where e.attemptId in :ids and e.kind in :kinds group by e.attemptId")
    List<Object[]> countByAttempt(@Param("ids") Collection<UUID> ids, @Param("kinds") Collection<PrepProctorEvent.Kind> kinds);

    /** Highest event number of an attempt (0 when none), so switch and AI events share one sequence. */
    @Query("select coalesce(max(e.seq), 0) from PrepProctorEvent e where e.attemptId = :attemptId")
    int maxSeq(@Param("attemptId") UUID attemptId);
}
