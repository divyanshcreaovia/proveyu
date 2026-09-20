package com.proveyu.recruiter.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_search_index")
@IdClass(CandidateSearchIndex.CandidateSearchIndexId.class)
public class CandidateSearchIndex {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skill_id", columnDefinition = "CHAR(36)")
    private UUID skillId;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_track", columnDefinition = "ENUM('FRESHER','MID','SENIOR')")
    private ExperienceTrack experienceTrack;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", columnDefinition = "ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED')")
    private VerificationStatus verificationStatus;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    public enum ExperienceTrack {
        FRESHER, MID, SENIOR
    }

    public enum VerificationStatus {
        UNVERIFIED, PENDING, VERIFIED, REJECTED
    }

    public CandidateSearchIndex() {}

    public CandidateSearchIndex(UUID candidateId, UUID skillId, BigDecimal scorePercent, String fullName, ExperienceTrack experienceTrack, VerificationStatus verificationStatus, Instant lastUpdated) {
        this.candidateId = candidateId;
        this.skillId = skillId;
        this.scorePercent = scorePercent;
        this.fullName = fullName;
        this.experienceTrack = experienceTrack;
        this.verificationStatus = verificationStatus;
        this.lastUpdated = lastUpdated;
    }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }

    public BigDecimal getScorePercent() { return scorePercent; }
    public void setScorePercent(BigDecimal scorePercent) { this.scorePercent = scorePercent; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public ExperienceTrack getExperienceTrack() { return experienceTrack; }
    public void setExperienceTrack(ExperienceTrack experienceTrack) { this.experienceTrack = experienceTrack; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public static class CandidateSearchIndexId implements Serializable {
        private UUID candidateId;
        private UUID skillId;

        public CandidateSearchIndexId() {}

        public CandidateSearchIndexId(UUID candidateId, UUID skillId) {
            this.candidateId = candidateId;
            this.skillId = skillId;
        }

        public UUID getCandidateId() { return candidateId; }
        public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

        public UUID getSkillId() { return skillId; }
        public void setSkillId(UUID skillId) { this.skillId = skillId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CandidateSearchIndexId that = (CandidateSearchIndexId) o;
            return Objects.equals(candidateId, that.candidateId) && Objects.equals(skillId, that.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(candidateId, skillId);
        }
    }
}
