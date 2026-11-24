package com.proveyu.student.prep.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prep_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepAttempt {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "test_id", columnDefinition = "CHAR(36)")
    private UUID testId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "deadline_at")
    private Instant deadlineAt;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    /** Why the test was submitted for the student (TAB_SWITCH), or null when the student or the timer submitted it. */
    @Column(name = "auto_submit_reason")
    private String autoSubmitReason;
    /** When the student agreed to camera + microphone monitoring (AI proctoring); null = not yet. */
    @Column(name = "consent_at")
    private Instant consentAt;
    /** AI proctoring events recorded for this attempt (counted apart from tab switches; they never submit the test). */
    @Column(name = "ai_warning_count")
    private int aiWarningCount;
    /** This attempt's questions (pool + order) and MCQ option order, as JSON; null = all questions in the original order. */
    @Column(name = "layout_json")
    private String layoutJson;
    /** Minutes added by an invigilator. */
    @Column(name = "extra_minutes")
    private int extraMinutes;
    /** Descriptive answers: null = none, PENDING = waiting for a teacher, DONE = marked. */
    @Column(name = "review_status")
    private String reviewStatus;
    private BigDecimal score;
    @Column(name = "max_score")
    private int maxScore;
    @Column(name = "answers_json")
    private String answersJson;
    @Column(name = "result_json")
    private String resultJson;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
