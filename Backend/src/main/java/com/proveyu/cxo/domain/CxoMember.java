package com.proveyu.cxo.domain;

import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** A CXO account's link to its college (one college per account). */
@Entity
@Table(name = "cxo_members")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CxoMember extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "designation")
    private String designation;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MemberStatus status = MemberStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
