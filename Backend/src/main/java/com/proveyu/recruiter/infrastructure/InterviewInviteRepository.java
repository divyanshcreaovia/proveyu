package com.proveyu.recruiter.infrastructure;

import com.proveyu.recruiter.domain.InterviewInvite;
import com.proveyu.recruiter.domain.InterviewInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewInviteRepository extends JpaRepository<InterviewInvite, UUID> {
    List<InterviewInvite> findByCandidateId(UUID candidateId);
    List<InterviewInvite> findByRecruiterId(UUID recruiterId);
    List<InterviewInvite> findByCandidateIdAndStatus(UUID candidateId, InterviewInviteStatus status);
}
