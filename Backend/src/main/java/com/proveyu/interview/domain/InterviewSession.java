package com.proveyu.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One AI mock interview of one student. */
@Entity
@Table(name = "ai_interview_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", columnDefinition = "CHAR(36)")
    private UUID collegeId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;
    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type")
    private InterviewModel.InterviewType interviewType;
    @Enumerated(EnumType.STRING)
    private InterviewModel.Difficulty difficulty;
    @Column(name = "role_title")
    private String roleTitle;
    @Enumerated(EnumType.STRING)
    private InterviewModel.SessionStatus status;
    @Column(name = "planned_questions")
    private int plannedQuestions;
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_source")
    private InterviewModel.CreditSource creditSource;
    @Column(name = "consent_at")
    private Instant consentAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
