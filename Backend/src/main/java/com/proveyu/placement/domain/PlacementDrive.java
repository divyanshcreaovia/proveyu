package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "placement_drive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementDrive {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID collegeId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID companyId;

    @Column(name = "offered_package", nullable = false, precision = 6, scale = 2)
    private BigDecimal offeredPackage;

    @Column(name = "drive_date")
    private LocalDate driveDate;

    @Column(name = "location", length = 255)
    private String location;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "placed_policy", nullable = false)
    private PlacedPolicy placedPolicy = PlacedPolicy.EXCLUDE_ALL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DriveStatus status = DriveStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
