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
public interface CollegeInviteRepository extends JpaRepository<CollegeInvite, UUID>, JpaSpecificationExecutor<CollegeInvite> {
    Optional<CollegeInvite> findByIdAndCollegeId(UUID id, UUID collegeId);
    Optional<CollegeInvite> findByTokenHash(String tokenHash);
    List<CollegeInvite> findAllByCollegeIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID collegeId);
    boolean existsByCollegeIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(UUID collegeId, String email);
}
