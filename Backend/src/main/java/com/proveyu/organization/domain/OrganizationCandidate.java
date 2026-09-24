package com.proveyu.organization.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_candidates")
public class OrganizationCandidate {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "organization_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @Column(name = "roll_number_or_id", length = 50)
    private String rollNumberOrId;

    @Column(name = "batch_year", length = 20)
    private String batchYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrganizationCandidate() {}

    public OrganizationCandidate(UUID id, UUID organizationId, UUID candidateId, String rollNumberOrId, String batchYear, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.candidateId = candidateId;
        this.rollNumberOrId = rollNumberOrId;
        this.batchYear = batchYear;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public String getRollNumberOrId() { return rollNumberOrId; }
    public void setRollNumberOrId(String rollNumberOrId) { this.rollNumberOrId = rollNumberOrId; }

    public String getBatchYear() { return batchYear; }
    public void setBatchYear(String batchYear) { this.batchYear = batchYear; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static OrganizationCandidateBuilder builder() { return new OrganizationCandidateBuilder(); }

    public static class OrganizationCandidateBuilder {
        private UUID id;
        private UUID organizationId;
        private UUID candidateId;
        private String rollNumberOrId;
        private String batchYear;
        private Instant createdAt;

        public OrganizationCandidateBuilder id(UUID id) { this.id = id; return this; }
        public OrganizationCandidateBuilder organizationId(UUID organizationId) { this.organizationId = organizationId; return this; }
        public OrganizationCandidateBuilder candidateId(UUID candidateId) { this.candidateId = candidateId; return this; }
        public OrganizationCandidateBuilder rollNumberOrId(String rollNumberOrId) { this.rollNumberOrId = rollNumberOrId; return this; }
        public OrganizationCandidateBuilder batchYear(String batchYear) { this.batchYear = batchYear; return this; }
        public OrganizationCandidateBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public OrganizationCandidate build() {
            return new OrganizationCandidate(id, organizationId, candidateId, rollNumberOrId, batchYear, createdAt);
        }
    }
}
