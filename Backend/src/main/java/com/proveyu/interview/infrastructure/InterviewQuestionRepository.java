package com.proveyu.interview.infrastructure;

import com.proveyu.interview.domain.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.UUID;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {
    List<InterviewQuestion> findAllBySessionIdOrderBySeq(UUID sessionId);
}
