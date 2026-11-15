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
public interface RoundResultRepository extends JpaRepository<RoundResult, UUID>, JpaSpecificationExecutor<RoundResult> {
    Optional<RoundResult> findByIdAndCollegeId(UUID id, UUID collegeId);
    List<RoundResult> findAllByCollegeIdAndRoundId(UUID collegeId, UUID roundId);
    Optional<RoundResult> findByRoundIdAndApplicationId(UUID roundId, UUID applicationId);
    List<RoundResult> findAllByCollegeIdAndApplicationIdIn(UUID collegeId, Collection<UUID> applicationIds);
}
