package com.proveyu.college.infrastructure;

import com.proveyu.college.domain.RoundScorecard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoundScorecardRepository extends JpaRepository<RoundScorecard, UUID> {
    List<RoundScorecard> findAllByCollegeIdAndRoundIdOrderByUpdatedAtDesc(UUID collegeId, UUID roundId);

    Optional<RoundScorecard> findByRoundIdAndApplicationIdAndUserId(UUID roundId, UUID applicationId, UUID userId);
}
