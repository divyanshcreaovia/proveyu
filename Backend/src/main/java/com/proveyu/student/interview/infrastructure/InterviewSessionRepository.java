package com.proveyu.student.interview.infrastructure;

import com.proveyu.student.interview.domain.InterviewModel;
import com.proveyu.student.interview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import com.proveyu.student.interview.domain.InterviewModel.SessionStatus;
import java.util.List;
import java.util.Optional;

import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    List<InterviewSession> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    List<InterviewSession> findAllByUserIdAndStatus(UUID userId, SessionStatus status);

    List<InterviewSession> findAllByStatus(SessionStatus status);

    Optional<InterviewSession> findFirstByStudentIdAndCreditSourceOrderByStartedAtDesc(UUID studentId, InterviewModel.CreditSource source);
}
