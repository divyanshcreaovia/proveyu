package com.proveyu.placement.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "placement_drives")
public class PlacementDrive {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "organization_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID organizationId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "company_website", length = 255)
    private String companyWebsite;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(length = 100)
    private String industry;

    @Column(nullable = false, length = 150)
    private String roles;

    @Column(name = "job_location", length = 150)
    private String jobLocation;

    @Column(name = "employment_type", length = 50)
    private String employmentType = "Full-Time";

    @Column(name = "package_lpa", nullable = false)
    private Double packageLpa = 0.0;

    @Column(name = "stipend_monthly", length = 50)
    private String stipendMonthly;

    @Column(name = "bond_duration_months")
    private Integer bondDurationMonths = 0;

    @Column(name = "drive_category", length = 100)
    private String driveCategory;

    @Column(name = "drive_date", length = 50)
    private String driveDate;

    @Column(name = "drive_mode", length = 50)
    private String driveMode = "On-Campus";

    @Column(length = 150)
    private String venue;

    @Column(name = "min_cgpa")
    private Double minCgpa = 0.0;

    @Column(name = "max_backlogs")
    private Integer maxBacklogs = 0;

    @Column(name = "max_gap_years")
    private Integer maxGapYears = 0;

    @Column(name = "min_tenth_percentage")
    private Double minTenthPercentage = 0.0;

    @Column(name = "min_twelfth_percentage")
    private Double minTwelfthPercentage = 0.0;

    @Column(name = "batch_filter", length = 50)
    private String batchFilter = "All";

    @Column(name = "degree_filter", length = 100)
    private String degreeFilter = "All";

    @Column(name = "min_proveyu_score")
    private Integer minProveYuScore = 0;

    @Column(name = "eligible_branches", columnDefinition = "TEXT")
    private String eligibleBranches;

    @Column(name = "selection_rounds", columnDefinition = "TEXT")
    private String selectionRounds;

    @Column(name = "hr_contact_name", length = 100)
    private String hrContactName;

    @Column(name = "hr_contact_email", length = 150)
    private String hrContactEmail;

    @Column(name = "hr_contact_phone", length = 30)
    private String hrContactPhone;

    @Column(name = "placement_eligibility", length = 100)
    private String placementEligibility = "Unplaced Only";

    @Column(name = "allow_placed_students")
    private boolean allowPlacedStudents = false;

    @Column(nullable = false, length = 50)
    private String status = "Scheduled"; // Scheduled, Ongoing, Completed, Cancelled

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlacementDrive() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCompanyWebsite() { return companyWebsite; }
    public void setCompanyWebsite(String companyWebsite) { this.companyWebsite = companyWebsite; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public String getJobLocation() { return jobLocation; }
    public void setJobLocation(String jobLocation) { this.jobLocation = jobLocation; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public Double getPackageLpa() { return packageLpa; }
    public void setPackageLpa(Double packageLpa) { this.packageLpa = packageLpa; }

    public String getStipendMonthly() { return stipendMonthly; }
    public void setStipendMonthly(String stipendMonthly) { this.stipendMonthly = stipendMonthly; }

    public Integer getBondDurationMonths() { return bondDurationMonths; }
    public void setBondDurationMonths(Integer bondDurationMonths) { this.bondDurationMonths = bondDurationMonths; }

    public String getDriveCategory() { return driveCategory; }
    public void setDriveCategory(String driveCategory) { this.driveCategory = driveCategory; }

    public String getDriveDate() { return driveDate; }
    public void setDriveDate(String driveDate) { this.driveDate = driveDate; }

    public String getDriveMode() { return driveMode; }
    public void setDriveMode(String driveMode) { this.driveMode = driveMode; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Double getMinCgpa() { return minCgpa; }
    public void setMinCgpa(Double minCgpa) { this.minCgpa = minCgpa; }

    public Integer getMaxBacklogs() { return maxBacklogs; }
    public void setMaxBacklogs(Integer maxBacklogs) { this.maxBacklogs = maxBacklogs; }

    public Integer getMaxGapYears() { return maxGapYears; }
    public void setMaxGapYears(Integer maxGapYears) { this.maxGapYears = maxGapYears; }

    public Double getMinTenthPercentage() { return minTenthPercentage; }
    public void setMinTenthPercentage(Double minTenthPercentage) { this.minTenthPercentage = minTenthPercentage; }

    public Double getMinTwelfthPercentage() { return minTwelfthPercentage; }
    public void setMinTwelfthPercentage(Double minTwelfthPercentage) { this.minTwelfthPercentage = minTwelfthPercentage; }

    public String getBatchFilter() { return batchFilter; }
    public void setBatchFilter(String batchFilter) { this.batchFilter = batchFilter; }

    public String getDegreeFilter() { return degreeFilter; }
    public void setDegreeFilter(String degreeFilter) { this.degreeFilter = degreeFilter; }

    public Integer getMinProveYuScore() { return minProveYuScore; }
    public void setMinProveYuScore(Integer minProveYuScore) { this.minProveYuScore = minProveYuScore; }

    public String getEligibleBranches() { return eligibleBranches; }
    public void setEligibleBranches(String eligibleBranches) { this.eligibleBranches = eligibleBranches; }

    public String getSelectionRounds() { return selectionRounds; }
    public void setSelectionRounds(String selectionRounds) { this.selectionRounds = selectionRounds; }

    public String getHrContactName() { return hrContactName; }
    public void setHrContactName(String hrContactName) { this.hrContactName = hrContactName; }

    public String getHrContactEmail() { return hrContactEmail; }
    public void setHrContactEmail(String hrContactEmail) { this.hrContactEmail = hrContactEmail; }

    public String getHrContactPhone() { return hrContactPhone; }
    public void setHrContactPhone(String hrContactPhone) { this.hrContactPhone = hrContactPhone; }

    public String getPlacementEligibility() { return placementEligibility; }
    public void setPlacementEligibility(String placementEligibility) { this.placementEligibility = placementEligibility; }

    public boolean isAllowPlacedStudents() { return allowPlacedStudents; }
    public void setAllowPlacedStudents(boolean allowPlacedStudents) { this.allowPlacedStudents = allowPlacedStudents; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
