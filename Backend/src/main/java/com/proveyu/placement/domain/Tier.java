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
@Table(name = "tier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tier {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tier_id", columnDefinition = "CHAR(36)")
    private UUID tierId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID collegeId;

    @Column(name = "tier_level", nullable = false)
    private int tierLevel;

    @Column(name = "tier_name", nullable = false, length = 50)
    private String tierName;

    @Column(name = "min_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal minScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
