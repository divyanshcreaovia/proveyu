package com.proveyu.placement.application.dto;

import com.proveyu.placement.domain.PlacementDrive1;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class PlacementDriveDto {

    public static class Request {
        @NotBlank(message = "Company name is required")
        private String companyName;

        private String companyWebsite;
        private String logoUrl;
        private String industry;

        @NotBlank(message = "Job role is required")
        private String roles;

        private String jobLocation;
        private String employmentType;

        @NotNull(message = "Package LPA is required")
        private Double packageLpa;

        private String stipendMonthly;
        private Integer bondDurationMonths;
        private String driveCategory;
        private String driveDate;
        private String driveMode;
        private String venue;
        private Double minCgpa;
        private Integer maxBacklogs;
        private Integer maxGapYears;
        private Double minTenthPercentage;
        private Double minTwelfthPercentage;
        private String batchFilter;
        private String degreeFilter;
        private Integer minProveYuScore;
        private String eligibleBranches;
        private String selectionRounds;
        private String hrContactName;
        private String hrContactEmail;
        private String hrContactPhone;
        private String placementEligibility;
        private Boolean allowPlacedStudents;
        private String status;

        public Request() {}

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

        public Boolean getAllowPlacedStudents() { return allowPlacedStudents; }
        public void setAllowPlacedStudents(Boolean allowPlacedStudents) { this.allowPlacedStudents = allowPlacedStudents; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class Response {
        private UUID id;
        private UUID organizationId;
        private String companyName;
        private String companyWebsite;
        private String logoUrl;
        private String industry;
        private String roles;
        private String jobLocation;
        private String employmentType;
        private Double packageLpa;
        private String stipendMonthly;
        private Integer bondDurationMonths;
        private String driveCategory;
        private String driveDate;
        private String driveMode;
        private String venue;
        private Double minCgpa;
        private Integer maxBacklogs;
        private Integer maxGapYears;
        private Double minTenthPercentage;
        private Double minTwelfthPercentage;
        private String batchFilter;
        private String degreeFilter;
        private Integer minProveYuScore;
        private String eligibleBranches;
        private String selectionRounds;
        private String hrContactName;
        private String hrContactEmail;
        private String hrContactPhone;
        private String placementEligibility;
        private Boolean allowPlacedStudents;
        private String status;

        public Response() {}

        public static Response fromEntity(PlacementDrive1 entity) {
            if (entity == null) return null;
            Response r = new Response();
            r.setId(entity.getId());
            r.setOrganizationId(entity.getOrganizationId());
            r.setCompanyName(entity.getCompanyName());
            r.setCompanyWebsite(entity.getCompanyWebsite());
            r.setLogoUrl(entity.getLogoUrl());
            r.setIndustry(entity.getIndustry());
            r.setRoles(entity.getRoles());
            r.setJobLocation(entity.getJobLocation());
            r.setEmploymentType(entity.getEmploymentType());
            r.setPackageLpa(entity.getPackageLpa());
            r.setStipendMonthly(entity.getStipendMonthly());
            r.setBondDurationMonths(entity.getBondDurationMonths());
            r.setDriveCategory(entity.getDriveCategory());
            r.setDriveDate(entity.getDriveDate());
            r.setDriveMode(entity.getDriveMode());
            r.setVenue(entity.getVenue());
            r.setMinCgpa(entity.getMinCgpa());
            r.setMaxBacklogs(entity.getMaxBacklogs());
            r.setMaxGapYears(entity.getMaxGapYears());
            r.setMinTenthPercentage(entity.getMinTenthPercentage());
            r.setMinTwelfthPercentage(entity.getMinTwelfthPercentage());
            r.setBatchFilter(entity.getBatchFilter());
            r.setDegreeFilter(entity.getDegreeFilter());
            r.setMinProveYuScore(entity.getMinProveYuScore());
            r.setEligibleBranches(entity.getEligibleBranches());
            r.setSelectionRounds(entity.getSelectionRounds());
            r.setHrContactName(entity.getHrContactName());
            r.setHrContactEmail(entity.getHrContactEmail());
            r.setHrContactPhone(entity.getHrContactPhone());
            r.setPlacementEligibility(entity.getPlacementEligibility());
            r.setAllowPlacedStudents(entity.isAllowPlacedStudents());
            r.setStatus(entity.getStatus());
            return r;
        }

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

        public Boolean getAllowPlacedStudents() { return allowPlacedStudents; }
        public void setAllowPlacedStudents(Boolean allowPlacedStudents) { this.allowPlacedStudents = allowPlacedStudents; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
