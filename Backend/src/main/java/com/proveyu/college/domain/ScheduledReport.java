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
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static com.proveyu.college.domain.CollegeEnums.*;

@Entity
@Table(name = "college_scheduled_reports")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledReport extends TenantEntity {
    @Column(name = "report_type")
    private String reportType;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "format")
    private ReportFormat format = ReportFormat.XLSX;
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency")
    private ReportFrequency frequency;
    @Column(name = "recipients")
    private String recipients;
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    @Column(name = "next_run_at")
    private Instant nextRunAt;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;
}
