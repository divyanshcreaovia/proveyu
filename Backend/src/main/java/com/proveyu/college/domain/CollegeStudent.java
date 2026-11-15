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
@Table(name = "college_students")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeStudent extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @Column(name = "roll_number")
    private String rollNumber;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "email")
    private String email;
    @Column(name = "phone")
    private String phone;
    @Column(name = "gender")
    private String gender;
    @Column(name = "dob")
    private LocalDate dob;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;
    @Column(name = "batch_year")
    private Integer batchYear;
    @Column(name = "degree")
    private String degree;
    @Column(name = "cgpa")
    private BigDecimal cgpa;
    @Column(name = "tenth_percent")
    private BigDecimal tenthPercent;
    @Column(name = "twelfth_percent")
    private BigDecimal twelfthPercent;
    @Builder.Default
    @Column(name = "active_backlogs")
    private int activeBacklogs = 0;
    @Builder.Default
    @Column(name = "gap_years")
    private int gapYears = 0;
    @Column(name = "skills")
    private String skills;
    @Column(name = "linkedin_url")
    private String linkedinUrl;
    @Column(name = "github_url")
    private String githubUrl;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StudentStatus status = StudentStatus.ACTIVE;
    @Column(name = "highest_ctc_lpa")
    private BigDecimal highestCtcLpa;
    @Column(name = "highest_tier_rank")
    private Integer highestTierRank;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
