package com.proveyu.recruiter.domain;

import com.proveyu.auth.domain.User;
import com.proveyu.candidate.domain.CandidateScore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_invitations", indexes = {
        @Index(name = "idx_job_inv_recruiter", columnList = "recruiter_id"),
        @Index(name = "idx_job_inv_candidate", columnList = "candidate_id"),
        @Index(name = "idx_job_inv_job", columnList = "job_id"),
        @Index(name = "idx_job_inv_cand_score", columnList = "candidate_score_id"),
        @Index(name = "idx_job_inv_status", columnList = "status")
})
public class JobInvitation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recruiter_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID recruiterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", insertable = false, updatable = false)
    private User recruiter;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", insertable = false, updatable = false)
    private User candidate;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "job_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false)
    private Job job;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_score_id", columnDefinition = "CHAR(36)")
    private UUID candidateScoreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_score_id", insertable = false, updatable = false)
    private CandidateScore candidateScore;

    @Column(name = "score_snapshot")
    private Double scoreSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JobInvitationStatus status = JobInvitationStatus.INVITED;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobInvitation() {}

    public JobInvitation(UUID id, UUID recruiterId, UUID candidateId, UUID jobId, UUID candidateScoreId,
                         Double scoreSnapshot, JobInvitationStatus status, String message, String notes,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.recruiterId = recruiterId;
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.candidateScoreId = candidateScoreId;
        this.scoreSnapshot = scoreSnapshot;
        this.status = status != null ? status : JobInvitationStatus.INVITED;
        this.message = message;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRecruiterId() { return recruiterId; }
    public void setRecruiterId(UUID recruiterId) { this.recruiterId = recruiterId; }

    public User getRecruiter() { return recruiter; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public User getCandidate() { return candidate; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public Job getJob() { return job; }

    public UUID getCandidateScoreId() { return candidateScoreId; }
    public void setCandidateScoreId(UUID candidateScoreId) { this.candidateScoreId = candidateScoreId; }

    public CandidateScore getCandidateScore() { return candidateScore; }

    public Double getScoreSnapshot() { return scoreSnapshot; }
    public void setScoreSnapshot(Double scoreSnapshot) { this.scoreSnapshot = scoreSnapshot; }

    public JobInvitationStatus getStatus() { return status; }
    public void setStatus(JobInvitationStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static JobInvitationBuilder builder() { return new JobInvitationBuilder(); }

    public static class JobInvitationBuilder {
        private UUID id;
        private UUID recruiterId;
        private UUID candidateId;
        private UUID jobId;
        private UUID candidateScoreId;
        private Double scoreSnapshot;
        private JobInvitationStatus status = JobInvitationStatus.INVITED;
        private String message;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;

        public JobInvitationBuilder id(UUID id) { this.id = id; return this; }
        public JobInvitationBuilder recruiterId(UUID recruiterId) { this.recruiterId = recruiterId; return this; }
        public JobInvitationBuilder candidateId(UUID candidateId) { this.candidateId = candidateId; return this; }
        public JobInvitationBuilder jobId(UUID jobId) { this.jobId = jobId; return this; }
        public JobInvitationBuilder candidateScoreId(UUID candidateScoreId) { this.candidateScoreId = candidateScoreId; return this; }
        public JobInvitationBuilder scoreSnapshot(Double scoreSnapshot) { this.scoreSnapshot = scoreSnapshot; return this; }
        public JobInvitationBuilder status(JobInvitationStatus status) { this.status = status; return this; }
        public JobInvitationBuilder message(String message) { this.message = message; return this; }
        public JobInvitationBuilder notes(String notes) { this.notes = notes; return this; }
        public JobInvitationBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public JobInvitationBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public JobInvitation build() {
            return new JobInvitation(id, recruiterId, candidateId, jobId, candidateScoreId, scoreSnapshot,
                    status, message, notes, createdAt, updatedAt);
        }
    }
}
