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
public interface CollegeApplicationRepository extends JpaRepository<CollegeApplication, UUID>, JpaSpecificationExecutor<CollegeApplication> {
    Optional<CollegeApplication> findByIdAndCollegeId(UUID id, UUID collegeId);
    List<CollegeApplication> findAllByCollegeIdAndDriveId(UUID collegeId, UUID driveId);
    Optional<CollegeApplication> findByDriveIdAndStudentId(UUID driveId, UUID studentId);
    List<CollegeApplication> findAllByCollegeIdAndStudentId(UUID collegeId, UUID studentId);
    List<CollegeApplication> findAllByCollegeId(UUID collegeId);
    List<CollegeApplication> findAllByIdInAndCollegeId(Collection<UUID> ids, UUID collegeId);
}
