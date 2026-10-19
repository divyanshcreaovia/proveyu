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
@Table(name = "placement_offer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementOffer {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "offer_id", columnDefinition = "CHAR(36)")
    private UUID offerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID driveId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID studentId;

    @Column(name = "role_name", length = 150)
    private String roleName;

    @Column(name = "package_lpa", nullable = false, precision = 6, scale = 2)
    private BigDecimal packageLpa;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_status", nullable = false)
    private OfferStatus offerStatus = OfferStatus.OFFERED;

    @CreationTimestamp
    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;
}
