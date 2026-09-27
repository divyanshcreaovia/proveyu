package com.proveyu.recruiter.infrastructure;

import com.proveyu.recruiter.domain.JobInvitation;
import com.proveyu.recruiter.domain.JobInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobInvitationRepository extends JpaRepository<JobInvitation, UUID> {
    List<JobInvitation> findByJobIdOrderByCreatedAtDesc(UUID jobId);
    List<JobInvitation> findByRecruiterIdOrderByCreatedAtDesc(UUID recruiterId);
    List<JobInvitation> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    List<JobInvitation> findByJobIdAndStatus(UUID jobId, JobInvitationStatus status);
    Optional<JobInvitation> findByJobIdAndCandidateId(UUID jobId, UUID candidateId);
    long countByJobId(UUID jobId);
    long countByJobIdAndStatus(UUID jobId, JobInvitationStatus status);
}
