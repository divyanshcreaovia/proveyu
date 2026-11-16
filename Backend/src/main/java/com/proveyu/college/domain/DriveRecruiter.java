package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A company person (HR or panel member) given access to one drive of one college. */
@Entity
@Table(name = "college_drive_recruiters")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DriveRecruiter extends TenantEntity {
    public enum Status { INVITED, ACTIVE, REVOKED }

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;
    @Column(name = "email")
    private String email;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "designation")
    private String designation;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.INVITED;
    @Column(name = "share_details")
    private boolean shareDetails;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "invited_by", columnDefinition = "CHAR(36)")
    private UUID invitedBy;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "last_access_at")
    private Instant lastAccessAt;
}
