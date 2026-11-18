package com.proveyu.student.interview.infrastructure;

import com.proveyu.student.interview.domain.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

import java.util.UUID;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {
    List<InterviewAnswer> findAllBySessionId(UUID sessionId);

    List<InterviewAnswer> findAllByAudioPathIsNotNullAndAnsweredAtBefore(Instant before);
}
