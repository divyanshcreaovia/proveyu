package com.proveyu.student.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** A planned question or a follow-up. */
@Entity
@Table(name = "ai_interview_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private UUID sessionId;
    /** Order asked. Planned questions use 100, 200, ...; a follow-up of question 200 gets 201, 202. */
    private int seq;
    @Column(name = "question_text")
    private String questionText;
    private String topic;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "parent_question_id", columnDefinition = "CHAR(36)")
    private UUID parentQuestionId;
    @Column(name = "asked_at")
    private Instant askedAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
