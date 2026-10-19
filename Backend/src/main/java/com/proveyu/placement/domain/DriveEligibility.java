package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "drive_eligibility")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveEligibility {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;

    @Column(name = "min_cgpa", precision = 4, scale = 2)
    private BigDecimal minCgpa;

    @Column(name = "min_score", precision = 5, scale = 2)
    private BigDecimal minScore;

    @Column(name = "min_10th_percent", precision = 5, scale = 2)
    private BigDecimal min10thPercent;

    @Column(name = "min_12th_percent", precision = 5, scale = 2)
    private BigDecimal min12thPercent;

    @Column(name = "max_active_backlogs")
    private Integer maxActiveBacklogs;

    @Column(name = "max_gap_years")
    private Integer maxGapYears;
}
