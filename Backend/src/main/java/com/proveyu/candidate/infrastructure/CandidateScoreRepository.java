package com.proveyu.candidate.infrastructure;

import com.proveyu.candidate.domain.CandidateScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateScoreRepository extends JpaRepository<CandidateScore, UUID> {
    List<CandidateScore> findByCandidateId(UUID candidateId);
    List<CandidateScore> findByCandidateIdAndExamId(UUID candidateId, UUID examId);
}
