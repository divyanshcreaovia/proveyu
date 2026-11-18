package com.proveyu.student.interview.infrastructure;

import com.proveyu.student.interview.domain.AnswerEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

import java.util.UUID;

public interface AnswerEvaluationRepository extends JpaRepository<AnswerEvaluation, UUID> {
    List<AnswerEvaluation> findAllByAnswerIdIn(Collection<UUID> answerIds);
}
