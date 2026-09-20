package com.proveyu.recruiter.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_invites")
public class InterviewInvite {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recruiter_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID recruiterId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", columnDefinition = "CHAR(36)")
    private UUID companyId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exam_id", columnDefinition = "CHAR(36)")
    private UUID examId;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "min_salary_lpa")
    private Double minSalaryLpa;

    @Column(name = "max_salary_lpa")
    private Double maxSalaryLpa;

    @Column(name = "package_range", length = 100)
    private String packageRange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewInviteStatus status = InterviewInviteStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement_status", nullable = false)
    private PlacementStatus placementStatus = PlacementStatus.IN_PROCESS;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InterviewInvite() {}

    public InterviewInvite(UUID id, UUID recruiterId, UUID companyId, UUID candidateId, UUID examId, String jobTitle, String jobDescription, Double minSalaryLpa, Double maxSalaryLpa, String packageRange, InterviewInviteStatus status, PlacementStatus placementStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.recruiterId = recruiterId;
        this.companyId = companyId;
        this.candidateId = candidateId;
        this.examId = examId;
        this.jobTitle = jobTitle;
        this.jobDescription = jobDescription;
        this.minSalaryLpa = minSalaryLpa;
        this.maxSalaryLpa = maxSalaryLpa;
        this.packageRange = packageRange;
        this.status = status != null ? status : InterviewInviteStatus.PENDING;
        this.placementStatus = placementStatus != null ? placementStatus : PlacementStatus.IN_PROCESS;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRecruiterId() { return recruiterId; }
    public void setRecruiterId(UUID recruiterId) { this.recruiterId = recruiterId; }

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public UUID getExamId() { return examId; }
    public void setExamId(UUID examId) { this.examId = examId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }

    public Double getMinSalaryLpa() { return minSalaryLpa; }
    public void setMinSalaryLpa(Double minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; }

    public Double getMaxSalaryLpa() { return maxSalaryLpa; }
    public void setMaxSalaryLpa(Double maxSalaryLpa) { this.maxSalaryLpa = maxSalaryLpa; }

    public String getPackageRange() { return packageRange; }
    public void setPackageRange(String packageRange) { this.packageRange = packageRange; }

    public InterviewInviteStatus getStatus() { return status; }
    public void setStatus(InterviewInviteStatus status) { this.status = status; }

    public PlacementStatus getPlacementStatus() { return placementStatus; }
    public void setPlacementStatus(PlacementStatus placementStatus) { this.placementStatus = placementStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static InterviewInviteBuilder builder() { return new InterviewInviteBuilder(); }

    public static class InterviewInviteBuilder {
        private UUID id;
        private UUID recruiterId;
        private UUID companyId;
        private UUID candidateId;
        private UUID examId;
        private String jobTitle;
        private String jobDescription;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String packageRange;
        private InterviewInviteStatus status = InterviewInviteStatus.PENDING;
        private PlacementStatus placementStatus = PlacementStatus.IN_PROCESS;
        private Instant createdAt;
        private Instant updatedAt;

        public InterviewInviteBuilder id(UUID id) { this.id = id; return this; }
        public InterviewInviteBuilder recruiterId(UUID recruiterId) { this.recruiterId = recruiterId; return this; }
        public InterviewInviteBuilder companyId(UUID companyId) { this.companyId = companyId; return this; }
        public InterviewInviteBuilder candidateId(UUID candidateId) { this.candidateId = candidateId; return this; }
        public InterviewInviteBuilder examId(UUID examId) { this.examId = examId; return this; }
        public InterviewInviteBuilder jobTitle(String jobTitle) { this.jobTitle = jobTitle; return this; }
        public InterviewInviteBuilder jobDescription(String jobDescription) { this.jobDescription = jobDescription; return this; }
        public InterviewInviteBuilder minSalaryLpa(Double minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; return this; }
        public InterviewInviteBuilder maxSalaryLpa(Double maxSalaryLpa) { this.maxSalaryLpa = maxSalaryLpa; return this; }
        public InterviewInviteBuilder packageRange(String packageRange) { this.packageRange = packageRange; return this; }
        public InterviewInviteBuilder status(InterviewInviteStatus status) { this.status = status; return this; }
        public InterviewInviteBuilder placementStatus(PlacementStatus placementStatus) { this.placementStatus = placementStatus; return this; }
        public InterviewInviteBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public InterviewInviteBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public InterviewInvite build() {
            return new InterviewInvite(id, recruiterId, companyId, candidateId, examId, jobTitle, jobDescription, minSalaryLpa, maxSalaryLpa, packageRange, status, placementStatus, createdAt, updatedAt);
        }
    }
}
