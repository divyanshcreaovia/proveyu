package com.proveyu.candidate.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_scores")
public class CandidateScore {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exam_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID examId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skill_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID skillId;

    @Column(nullable = false)
    private double score;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    @Column(nullable = false)
    private boolean passed = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CandidateScore() {}

    public CandidateScore(UUID id, UUID candidateId, UUID examId, UUID skillId, double score, double maxScore, boolean passed, Instant createdAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.examId = examId;
        this.skillId = skillId;
        this.score = score;
        this.maxScore = maxScore;
        this.passed = passed;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public UUID getExamId() { return examId; }
    public void setExamId(UUID examId) { this.examId = examId; }

    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public double getMaxScore() { return maxScore; }
    public void setMaxScore(double maxScore) { this.maxScore = maxScore; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static CandidateScoreBuilder builder() { return new CandidateScoreBuilder(); }

    public static class CandidateScoreBuilder {
        private UUID id;
        private UUID candidateId;
        private UUID examId;
        private UUID skillId;
        private double score;
        private double maxScore;
        private boolean passed = true;
        private Instant createdAt;

        public CandidateScoreBuilder id(UUID id) { this.id = id; return this; }
        public CandidateScoreBuilder candidateId(UUID candidateId) { this.candidateId = candidateId; return this; }
        public CandidateScoreBuilder examId(UUID examId) { this.examId = examId; return this; }
        public CandidateScoreBuilder skillId(UUID skillId) { this.skillId = skillId; return this; }
        public CandidateScoreBuilder score(double score) { this.score = score; return this; }
        public CandidateScoreBuilder maxScore(double maxScore) { this.maxScore = maxScore; return this; }
        public CandidateScoreBuilder passed(boolean passed) { this.passed = passed; return this; }
        public CandidateScoreBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public CandidateScore build() {
            return new CandidateScore(id, candidateId, examId, skillId, score, maxScore, passed, createdAt);
        }
    }
}
