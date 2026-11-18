package com.proveyu.student.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** The student's answer: transcript plus the stored audio file. */
@Entity
@Table(name = "ai_interview_answers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswer {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private UUID sessionId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "question_id", columnDefinition = "CHAR(36)")
    private UUID questionId;
    private String transcript;
    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode")
    private InterviewModel.InputMode inputMode;
    @Column(name = "audio_path")
    private String audioPath;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @Column(name = "words_per_minute")
    private Integer wordsPerMinute;
    @Column(name = "answered_at")
    private Instant answeredAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
