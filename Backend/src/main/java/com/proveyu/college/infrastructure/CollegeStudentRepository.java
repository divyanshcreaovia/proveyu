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
public interface CollegeStudentRepository extends JpaRepository<CollegeStudent, UUID>, JpaSpecificationExecutor<CollegeStudent> {
    Optional<CollegeStudent> findByIdAndCollegeId(UUID id, UUID collegeId);
    Optional<CollegeStudent> findByCollegeIdAndRollNumberIgnoreCase(UUID collegeId, String rollNumber);
    Optional<CollegeStudent> findByCollegeIdAndEmailIgnoreCase(UUID collegeId, String email);
    List<CollegeStudent> findAllByCollegeIdAndPhone(UUID collegeId, String phone);
    List<CollegeStudent> findAllByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    List<CollegeStudent> findAllByIdInAndCollegeId(Collection<UUID> ids, UUID collegeId);
    Optional<CollegeStudent> findFirstByUserIdAndDeletedAtIsNull(UUID userId);
    List<CollegeStudent> findAllByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    long countByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    boolean existsByUserIdAndCollegeIdNotAndDeletedAtIsNull(UUID userId, UUID collegeId);

    /** Row locks: requests that change the same student's offers wait for each other (use with READ_COMMITTED). */
    @org.springframework.data.jpa.repository.Query(value = "select id from college_students where id = ?1 and college_id = ?2 for update", nativeQuery = true)
    java.util.List<String> lockRow(String id, String collegeId);
    @org.springframework.data.jpa.repository.Query(value = "select id from college_students where user_id = ?1 and deleted_at is null for update", nativeQuery = true)
    java.util.List<String> lockRowsOfUser(String userId);
}
