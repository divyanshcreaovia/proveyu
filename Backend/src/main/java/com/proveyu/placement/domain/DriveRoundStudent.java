package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drive_round_student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveRoundStudent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_round_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID driveRoundId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID studentId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoundStudentStatus status = RoundStudentStatus.SHORTLISTED;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "mail_sent_at")
    private Instant mailSentAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
