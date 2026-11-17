package com.proveyu.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** AI score for one answer (1..5 on each rubric line). */
@Entity
@Table(name = "ai_answer_evaluations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerEvaluation {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "answer_id", columnDefinition = "CHAR(36)")
    private UUID answerId;
    private int content;
    private int communication;
    private int confidence;
    private int relevance;
    private String feedback;
    @Column(name = "better_answer")
    private String betterAnswer;
    private String model;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
