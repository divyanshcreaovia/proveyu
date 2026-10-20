package com.proveyu.placement.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_org_roll", columnNames = {"organization_id", "roll_number"})
})
public class StudentProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "organization_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID userId;

    @Column(name = "roll_number", nullable = false, length = 50)
    private String rollNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 25)
    private String phone;

    @Column(length = 20)
    private String dob;

    @Column(length = 20)
    private String gender;

    @Column(name = "aadhar_number", length = 50)
    private String aadharNumber;

    @Column(name = "pan_number", length = 50)
    private String panNumber;

    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress;

    @Column(name = "college_name", length = 255)
    private String collegeName;

    @Column(name = "batch_year", nullable = false, length = 20)
    private String batchYear;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(nullable = false, length = 100)
    private String degree;

    @Column(name = "tenth_percentage")
    private Double tenthPercentage;

    @Column(name = "twelfth_percentage")
    private Double twelfthPercentage;

    @Column(name = "active_backlogs", nullable = false)
    private int activeBacklogs = 0;

    @Column(name = "gap_years", nullable = false)
    private int gapYears = 0;

    @Column(name = "grad_date", length = 50)
    private String gradDate;

    // Semesters 1 through 8 SGPA
    @Column(name = "sem1_sgpa")
    private Double sem1Sgpa;

    @Column(name = "sem2_sgpa")
    private Double sem2Sgpa;

    @Column(name = "sem3_sgpa")
    private Double sem3Sgpa;

    @Column(name = "sem4_sgpa")
    private Double sem4Sgpa;

    @Column(name = "sem5_sgpa")
    private Double sem5Sgpa;

    @Column(name = "sem6_sgpa")
    private Double sem6Sgpa;

    @Column(name = "sem7_sgpa")
    private Double sem7Sgpa;

    @Column(name = "sem8_sgpa")
    private Double sem8Sgpa;

    @Column(name = "aggregate_cgpa", nullable = false)
    private Double aggregateCgpa = 0.0;

    @Column(nullable = false, length = 20)
    private String tier = "Tier 2";

    @Column(name = "tier_badge", length = 50)
    private String tierBadge = "badge-tier-2";

    @Column(name = "prove_yu_score", length = 20)
    private String proveYuScore = "75/100";

    @Column(name = "coding_score")
    private Integer codingScore = 25;

    @Column(name = "aptitude_score")
    private Integer aptitudeScore = 25;

    @Column(name = "comm_score")
    private Integer commScore = 25;

    // Placement Tracker Fields
    @Column(name = "placement_status", nullable = false, length = 30)
    private String placementStatus = "Unplaced"; // Unplaced, Placed, In Process

    @Column(name = "placed_company", length = 150)
    private String placedCompany;

    @Column(name = "placed_role", length = 150)
    private String placedRole;

    @Column(name = "placed_ctc", length = 50)
    private String placedCtc;

    @Column(name = "package_category", length = 100)
    private String packageCategory;

    @Column(name = "bond_duration", length = 50)
    private String bondDuration;

    @Column(name = "offer_date")
    private LocalDate offerDate;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "recruitment_source", length = 100)
    private String recruitmentSource;

    // Social Links
    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "github_url", length = 255)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 255)
    private String portfolioUrl;

    @Column(name = "resume_uploaded", nullable = false)
    private boolean resumeUploaded = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StudentProfile() {}

    public void recalculateCgpa() {
        Double[] s = {sem1Sgpa, sem2Sgpa, sem3Sgpa, sem4Sgpa, sem5Sgpa, sem6Sgpa, sem7Sgpa, sem8Sgpa};
        double sum = 0;
        int count = 0;
        for (Double val : s) {
            if (val != null && val > 0) {
                sum += val;
                count++;
            }
        }
        if (count > 0) {
            this.aggregateCgpa = Math.round((sum / count) * 100.0) / 100.0;
        }
        // Update tier based on CGPA
        if (this.aggregateCgpa >= 8.0) {
            this.tier = "Tier 1";
            this.tierBadge = "badge-tier-1";
        } else if (this.aggregateCgpa >= 6.5) {
            this.tier = "Tier 2";
            this.tierBadge = "badge-tier-2";
        } else {
            this.tier = "Tier 3";
            this.tierBadge = "badge-tier-3";
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAadharNumber() { return aadharNumber; }
    public void setAadharNumber(String aadharNumber) { this.aadharNumber = aadharNumber; }

    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }

    public String getResidentialAddress() { return residentialAddress; }
    public void setResidentialAddress(String residentialAddress) { this.residentialAddress = residentialAddress; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getBatchYear() { return batchYear; }
    public void setBatchYear(String batchYear) { this.batchYear = batchYear; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public Double getTenthPercentage() { return tenthPercentage; }
    public void setTenthPercentage(Double tenthPercentage) { this.tenthPercentage = tenthPercentage; }

    public Double getTwelfthPercentage() { return twelfthPercentage; }
    public void setTwelfthPercentage(Double twelfthPercentage) { this.twelfthPercentage = twelfthPercentage; }

    public int getActiveBacklogs() { return activeBacklogs; }
    public void setActiveBacklogs(int activeBacklogs) { this.activeBacklogs = activeBacklogs; }

    public int getGapYears() { return gapYears; }
    public void setGapYears(int gapYears) { this.gapYears = gapYears; }

    public String getGradDate() { return gradDate; }
    public void setGradDate(String gradDate) { this.gradDate = gradDate; }

    public Double getSem1Sgpa() { return sem1Sgpa; }
    public void setSem1Sgpa(Double sem1Sgpa) { this.sem1Sgpa = sem1Sgpa; }

    public Double getSem2Sgpa() { return sem2Sgpa; }
    public void setSem2Sgpa(Double sem2Sgpa) { this.sem2Sgpa = sem2Sgpa; }

    public Double getSem3Sgpa() { return sem3Sgpa; }
    public void setSem3Sgpa(Double sem3Sgpa) { this.sem3Sgpa = sem3Sgpa; }

    public Double getSem4Sgpa() { return sem4Sgpa; }
    public void setSem4Sgpa(Double sem4Sgpa) { this.sem4Sgpa = sem4Sgpa; }

    public Double getSem5Sgpa() { return sem5Sgpa; }
    public void setSem5Sgpa(Double sem5Sgpa) { this.sem5Sgpa = sem5Sgpa; }

    public Double getSem6Sgpa() { return sem6Sgpa; }
    public void setSem6Sgpa(Double sem6Sgpa) { this.sem6Sgpa = sem6Sgpa; }

    public Double getSem7Sgpa() { return sem7Sgpa; }
    public void setSem7Sgpa(Double sem7Sgpa) { this.sem7Sgpa = sem7Sgpa; }

    public Double getSem8Sgpa() { return sem8Sgpa; }
    public void setSem8Sgpa(Double sem8Sgpa) { this.sem8Sgpa = sem8Sgpa; }

    public Double getAggregateCgpa() { return aggregateCgpa; }
    public void setAggregateCgpa(Double aggregateCgpa) { this.aggregateCgpa = aggregateCgpa; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getTierBadge() { return tierBadge; }
    public void setTierBadge(String tierBadge) { this.tierBadge = tierBadge; }

    public String getProveYuScore() { return proveYuScore; }
    public void setProveYuScore(String proveYuScore) { this.proveYuScore = proveYuScore; }

    public Integer getCodingScore() { return codingScore; }
    public void setCodingScore(Integer codingScore) { this.codingScore = codingScore; }

    public Integer getAptitudeScore() { return aptitudeScore; }
    public void setAptitudeScore(Integer aptitudeScore) { this.aptitudeScore = aptitudeScore; }

    public Integer getCommScore() { return commScore; }
    public void setCommScore(Integer commScore) { this.commScore = commScore; }

    public String getPlacementStatus() { return placementStatus; }
    public void setPlacementStatus(String placementStatus) { this.placementStatus = placementStatus; }

    public String getPlacedCompany() { return placedCompany; }
    public void setPlacedCompany(String placedCompany) { this.placedCompany = placedCompany; }

    public String getPlacedRole() { return placedRole; }
    public void setPlacedRole(String placedRole) { this.placedRole = placedRole; }

    public String getPlacedCtc() { return placedCtc; }
    public void setPlacedCtc(String placedCtc) { this.placedCtc = placedCtc; }

    public String getPackageCategory() { return packageCategory; }
    public void setPackageCategory(String packageCategory) { this.packageCategory = packageCategory; }

    public String getBondDuration() { return bondDuration; }
    public void setBondDuration(String bondDuration) { this.bondDuration = bondDuration; }

    public LocalDate getOfferDate() { return offerDate; }
    public void setOfferDate(LocalDate offerDate) { this.offerDate = offerDate; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public String getRecruitmentSource() { return recruitmentSource; }
    public void setRecruitmentSource(String recruitmentSource) { this.recruitmentSource = recruitmentSource; }

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }

    public boolean isResumeUploaded() { return resumeUploaded; }
    public void setResumeUploaded(boolean resumeUploaded) { this.resumeUploaded = resumeUploaded; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
