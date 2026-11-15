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
public interface CollegeOfferRepository extends JpaRepository<CollegeOffer, UUID>, JpaSpecificationExecutor<CollegeOffer> {
    Optional<CollegeOffer> findByIdAndCollegeId(UUID id, UUID collegeId);
    List<CollegeOffer> findAllByCollegeId(UUID collegeId);
    List<CollegeOffer> findAllByCollegeIdAndStudentId(UUID collegeId, UUID studentId);
    List<CollegeOffer> findAllByCollegeIdAndDriveId(UUID collegeId, UUID driveId);
}
