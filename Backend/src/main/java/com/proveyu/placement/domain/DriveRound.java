package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drive_round")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveRound {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_round_id", columnDefinition = "CHAR(36)")
    private UUID driveRoundId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID driveId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "round_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID roundId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "round_date")
    private LocalDate roundDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoundStatus status = RoundStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
