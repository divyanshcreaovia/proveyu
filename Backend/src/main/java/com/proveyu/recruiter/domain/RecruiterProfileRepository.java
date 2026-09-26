package com.proveyu.recruiter.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, UUID> {
}
