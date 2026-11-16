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
}
