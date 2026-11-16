package com.proveyu.college.infrastructure;

import com.proveyu.college.domain.AnnouncementRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, UUID> {
    Page<AnnouncementRecipient> findAllByCollegeIdAndStudentIdOrderByCreatedAtDesc(UUID collegeId, UUID studentId, Pageable pageable);

    Optional<AnnouncementRecipient> findByCollegeIdAndStudentIdAndAnnouncementId(UUID collegeId, UUID studentId, UUID announcementId);

    long countByCollegeIdAndStudentIdAndReadAtIsNull(UUID collegeId, UUID studentId);

    @Modifying
    @Query("update AnnouncementRecipient r set r.readAt = :now where r.collegeId = :collegeId and r.studentId = :studentId and r.readAt is null")
    int markAllRead(@Param("collegeId") UUID collegeId, @Param("studentId") UUID studentId, @Param("now") Instant now);

    /** [announcementId, readCount] pairs for the college's announcement list. */
    @Query("select r.announcementId, count(r) from AnnouncementRecipient r where r.announcementId in :ids and r.readAt is not null group by r.announcementId")
    List<Object[]> readCounts(@Param("ids") Collection<UUID> ids);
}
