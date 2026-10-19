package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID collegeId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "branch_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID branchId;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    @Column(name = "cgpa", precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "tenth_percent", precision = 5, scale = 2)
    private BigDecimal tenthPercent;

    @Column(name = "twelfth_percent", precision = 5, scale = 2)
    private BigDecimal twelfthPercent;

    @Builder.Default
    @Column(name = "active_backlogs", nullable = false)
    private int activeBacklogs = 0;

    @Builder.Default
    @Column(name = "gap_years", nullable = false)
    private int gapYears = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "placed_status", nullable = false)
    private PlacedStatus placedStatus = PlacedStatus.UNPLACED;

    @Column(name = "highest_package_lpa", precision = 6, scale = 2)
    private BigDecimal highestPackageLpa;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
