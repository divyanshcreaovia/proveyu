package com.proveyu.student.prep.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One time a student left a running mock test, with a screenshot of the shared screen as proof. */
@Entity
@Table(name = "prep_proctor_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepProctorEvent {

    /** TAB_HIDDEN: switched tab or minimised; WINDOW_BLUR: moved to another app or window; SHARE_STOPPED: stopped sharing the screen. */
    public enum Kind { TAB_HIDDEN, WINDOW_BLUR, SHARE_STOPPED }

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attempt_id", columnDefinition = "CHAR(36)")
    private UUID attemptId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private int seq;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "client_at")
    private Instant clientAt;
    @Column(name = "screenshot_path")
    private String screenshotPath;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
