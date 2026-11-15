package com.proveyu.college.infrastructure;

import com.proveyu.college.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.proveyu.college.domain.CollegeEnums.*;

/** All lookups are tenant-scoped: callers must pass the college id resolved from the authenticated principal. */
public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, UUID>, JpaSpecificationExecutor<InterviewSlot> {
    Optional<InterviewSlot> findByIdAndCollegeId(UUID id, UUID collegeId);
    List<InterviewSlot> findAllByCollegeIdAndStartsAtBetweenOrderByStartsAtAsc(UUID collegeId, Instant from, Instant to);
    List<InterviewSlot> findAllByCollegeIdAndDriveIdOrderByStartsAtAsc(UUID collegeId, UUID driveId);
    List<InterviewSlot> findAllByStatusAndReminderSentAtIsNullAndStartsAtBetween(SlotStatus status, Instant from, Instant to);
    @Query("select s from InterviewSlot s where s.collegeId = :collegeId and s.status = 'SCHEDULED' and s.startsAt < :end and s.endsAt > :start and (:excludeId is null or s.id <> :excludeId)")
    List<InterviewSlot> findOverlapping(@Param("collegeId") UUID collegeId, @Param("start") Instant start, @Param("end") Instant end, @Param("excludeId") UUID excludeId);
}
