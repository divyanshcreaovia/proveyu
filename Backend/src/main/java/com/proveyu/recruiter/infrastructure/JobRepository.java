package com.proveyu.recruiter.infrastructure;

import com.proveyu.recruiter.domain.Job;
import com.proveyu.recruiter.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByPostedByOrderByCreatedAtDesc(UUID postedBy);
    List<Job> findByPostedByAndStatusOrderByCreatedAtDesc(UUID postedBy, JobStatus status);
    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);
}
