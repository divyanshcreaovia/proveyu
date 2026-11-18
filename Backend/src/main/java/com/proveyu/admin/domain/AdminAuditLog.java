package com.proveyu.admin.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One action of a platform admin. */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "admin_id", columnDefinition = "CHAR(36)")
    private UUID adminId;
    @Column(name = "admin_name")
    private String adminName;
    private String action;
    @Column(name = "target_type")
    private String targetType;
    @Column(name = "target_id")
    private String targetId;
    private String summary;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
