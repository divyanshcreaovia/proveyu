package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "college_drives")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeDrive extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", columnDefinition = "CHAR(36)")
    private UUID companyId;
    @Column(name = "title")
    private String title;
    @Column(name = "role_title")
    private String roleTitle;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type")
    private DriveType driveType = DriveType.ON_CAMPUS;
    @Column(name = "ctc_lpa")
    private BigDecimal ctcLpa;
    @Column(name = "stipend_monthly")
    private BigDecimal stipendMonthly;
    @Column(name = "ctc_breakup")
    private String ctcBreakup;
    @Column(name = "location")
    private String location;
    @Builder.Default
    @Column(name = "bond_months")
    private int bondMonths = 0;
    @Column(name = "bond_details")
    private String bondDetails;
    @Column(name = "job_description")
    private String jobDescription;
    @Column(name = "skills")
    private String skills;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private DriveMode mode = DriveMode.OFFLINE;
    @Column(name = "venue")
    private String venue;
    @Column(name = "registration_deadline")
    private Instant registrationDeadline;
    @Column(name = "drive_date")
    private LocalDate driveDate;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tier_id", columnDefinition = "CHAR(36)")
    private UUID tierId;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DriveStatus status = DriveStatus.DRAFT;
    @Column(name = "min_cgpa")
    private BigDecimal minCgpa;
    @Column(name = "min_tenth")
    private BigDecimal minTenth;
    @Column(name = "min_twelfth")
    private BigDecimal minTwelfth;
    @Column(name = "max_active_backlogs")
    private Integer maxActiveBacklogs;
    @Column(name = "max_gap_years")
    private Integer maxGapYears;
    @Column(name = "batch_years")
    private String batchYears;
    @Column(name = "branch_ids")
    private String branchIds;
    @Column(name = "required_skills")
    private String requiredSkills;
    @Column(name = "published_at")
    private Instant publishedAt;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
