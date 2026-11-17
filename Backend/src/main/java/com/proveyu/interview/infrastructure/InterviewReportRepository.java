package com.proveyu.interview.infrastructure;

import com.proveyu.interview.domain.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import java.util.UUID;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, UUID> {
    Optional<InterviewReport> findBySessionId(UUID sessionId);
}
