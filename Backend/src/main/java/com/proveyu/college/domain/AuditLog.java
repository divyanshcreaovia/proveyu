package com.proveyu.college.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail of sensitive college actions. */
@Entity
@Table(name = "college_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", columnDefinition = "CHAR(36)", updatable = false) private UUID collegeId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_user_id", columnDefinition = "CHAR(36)") private UUID actorUserId;
    @Column(name = "actor_name") private String actorName;
    @Column(name = "action") private String action;
    @Column(name = "entity_type") private String entityType;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entity_id", columnDefinition = "CHAR(36)") private UUID entityId;
    @Column(name = "summary") private String summary;
    @Column(name = "details") private String details;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;

    @PrePersist
    void assignId() { if (id == null) id = UUID.randomUUID(); }
}
