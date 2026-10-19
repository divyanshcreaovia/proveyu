package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "package_tier_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageTierRule {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rule_id", columnDefinition = "CHAR(36)")
    private UUID ruleId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID collegeId;

    @Column(name = "min_package_lpa", nullable = false, precision = 6, scale = 2)
    private BigDecimal minPackageLpa;

    @Column(name = "max_package_lpa", precision = 6, scale = 2)
    private BigDecimal maxPackageLpa;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tier_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID tierId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
