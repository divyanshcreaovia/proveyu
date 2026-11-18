package com.proveyu.student.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Final result of an interview. */
@Entity
@Table(name = "ai_interview_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReport {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private UUID sessionId;
    @Column(name = "overall_score")
    private BigDecimal overallScore;
    @Enumerated(EnumType.STRING)
    private InterviewModel.Recommendation recommendation;
    private String summary;
    @Column(name = "strengths_json")
    private String strengthsJson;
    @Column(name = "improvements_json")
    private String improvementsJson;
    private String model;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
