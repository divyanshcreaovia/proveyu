package com.proveyu.placement.application.dto;

import com.proveyu.placement.domain.StudentProfile;

import java.time.LocalDate;
import java.util.UUID;

public class StudentProfileDto {
    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String rollNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String dob;
    private String gender;
    private String aadharNumber;
    private String panNumber;
    private String residentialAddress;
    private String collegeName;
    private String batchYear;
    private String branch;
    private String degree;
    private Double tenthPercentage;
    private Double twelfthPercentage;
    private Integer activeBacklogs;
    private Integer gapYears;
    private String gradDate;

    // Semesters 1 through 8 SGPA
    private Double sem1Sgpa;
    private Double sem2Sgpa;
    private Double sem3Sgpa;
    private Double sem4Sgpa;
    private Double sem5Sgpa;
    private Double sem6Sgpa;
    private Double sem7Sgpa;
    private Double sem8Sgpa;
    private Double aggregateCgpa;

    private String tier;
    private String tierBadge;
    private String proveYuScore;
    private Integer codingScore;
    private Integer aptitudeScore;
    private Integer commScore;

    // Placement Tracker Fields
    private String placementStatus;
    private String placedCompany;
    private String placedRole;
    private String placedCtc;
    private String packageCategory;
    private String bondDuration;
    private LocalDate offerDate;
    private LocalDate joiningDate;
    private String recruitmentSource;

    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private Boolean resumeUploaded;

    public StudentProfileDto() {}

    public static StudentProfileDto fromEntity(StudentProfile entity) {
        if (entity == null) return null;
        StudentProfileDto dto = new StudentProfileDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setUserId(entity.getUserId());
        dto.setRollNumber(entity.getRollNumber());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setDob(entity.getDob());
        dto.setGender(entity.getGender());
        dto.setAadharNumber(entity.getAadharNumber());
        dto.setPanNumber(entity.getPanNumber());
        dto.setResidentialAddress(entity.getResidentialAddress());
        dto.setCollegeName(entity.getCollegeName());
        dto.setBatchYear(entity.getBatchYear());
        dto.setBranch(entity.getBranch());
        dto.setDegree(entity.getDegree());
        dto.setTenthPercentage(entity.getTenthPercentage());
        dto.setTwelfthPercentage(entity.getTwelfthPercentage());
        dto.setActiveBacklogs(entity.getActiveBacklogs());
        dto.setGapYears(entity.getGapYears());
        dto.setGradDate(entity.getGradDate());
        dto.setSem1Sgpa(entity.getSem1Sgpa());
        dto.setSem2Sgpa(entity.getSem2Sgpa());
        dto.setSem3Sgpa(entity.getSem3Sgpa());
        dto.setSem4Sgpa(entity.getSem4Sgpa());
        dto.setSem5Sgpa(entity.getSem5Sgpa());
        dto.setSem6Sgpa(entity.getSem6Sgpa());
        dto.setSem7Sgpa(entity.getSem7Sgpa());
        dto.setSem8Sgpa(entity.getSem8Sgpa());
        dto.setAggregateCgpa(entity.getAggregateCgpa());
        dto.setTier(entity.getTier());
        dto.setTierBadge(entity.getTierBadge());
        dto.setProveYuScore(entity.getProveYuScore());
        dto.setCodingScore(entity.getCodingScore());
        dto.setAptitudeScore(entity.getAptitudeScore());
        dto.setCommScore(entity.getCommScore());
        dto.setPlacementStatus(entity.getPlacementStatus());
        dto.setPlacedCompany(entity.getPlacedCompany());
        dto.setPlacedRole(entity.getPlacedRole());
        dto.setPlacedCtc(entity.getPlacedCtc());
        dto.setPackageCategory(entity.getPackageCategory());
        dto.setBondDuration(entity.getBondDuration());
        dto.setOfferDate(entity.getOfferDate());
        dto.setJoiningDate(entity.getJoiningDate());
        dto.setRecruitmentSource(entity.getRecruitmentSource());
        dto.setLinkedinUrl(entity.getLinkedinUrl());
        dto.setGithubUrl(entity.getGithubUrl());
        dto.setPortfolioUrl(entity.getPortfolioUrl());
        dto.setResumeUploaded(entity.isResumeUploaded());
        return dto;
    }

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

    public Integer getActiveBacklogs() { return activeBacklogs; }
    public void setActiveBacklogs(Integer activeBacklogs) { this.activeBacklogs = activeBacklogs; }

    public Integer getGapYears() { return gapYears; }
    public void setGapYears(Integer gapYears) { this.gapYears = gapYears; }

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

    public Boolean getResumeUploaded() { return resumeUploaded; }
    public void setResumeUploaded(Boolean resumeUploaded) { this.resumeUploaded = resumeUploaded; }
}
