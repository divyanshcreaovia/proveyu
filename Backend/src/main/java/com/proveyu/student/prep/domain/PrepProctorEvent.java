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

    /**
     * Leaving the test (counted as tab switches, may submit it): TAB_HIDDEN switched tab or minimised; WINDOW_BLUR moved to another
     * app or window; SHARE_STOPPED stopped sharing the screen.
     * AI proctoring (camera + microphone, only flag the attempt for review): NO_FACE, MULTIPLE_FACES, LOOKING_AWAY, CAMERA_BLOCKED,
     * VOICE_DETECTED.
     */
    public enum Kind {
        TAB_HIDDEN, WINDOW_BLUR, SHARE_STOPPED,
        NO_FACE, MULTIPLE_FACES, LOOKING_AWAY, CAMERA_BLOCKED, VOICE_DETECTED;

        public static final java.util.Set<Kind> SWITCH_KINDS = java.util.EnumSet.of(TAB_HIDDEN, WINDOW_BLUR, SHARE_STOPPED);

        public boolean isAi() { return !SWITCH_KINDS.contains(this); }
    }

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
    /** AI events: small JSON from the browser (faces seen, head angle, speech length). */
    private String metadata;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
