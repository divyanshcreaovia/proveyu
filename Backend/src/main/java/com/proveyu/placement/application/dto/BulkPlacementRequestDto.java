package com.proveyu.placement.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BulkPlacementRequestDto {

    @NotEmpty(message = "At least one student must be selected")
    private List<UUID> studentIds;

    @NotBlank(message = "Company name is required")
    private String company;

    @NotBlank(message = "Role offered is required")
    private String role;

    @NotBlank(message = "CTC package is required")
    private String ctc;

    private String packageCategory;
    private String bondDuration;
    private String recruitmentSource;
    private LocalDate offerDate;
    private LocalDate joiningDate;

    public BulkPlacementRequestDto() {}

    public List<UUID> getStudentIds() { return studentIds; }
    public void setStudentIds(List<UUID> studentIds) { this.studentIds = studentIds; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCtc() { return ctc; }
    public void setCtc(String ctc) { this.ctc = ctc; }

    public String getPackageCategory() { return packageCategory; }
    public void setPackageCategory(String packageCategory) { this.packageCategory = packageCategory; }

    public String getBondDuration() { return bondDuration; }
    public void setBondDuration(String bondDuration) { this.bondDuration = bondDuration; }

    public String getRecruitmentSource() { return recruitmentSource; }
    public void setRecruitmentSource(String recruitmentSource) { this.recruitmentSource = recruitmentSource; }

    public LocalDate getOfferDate() { return offerDate; }
    public void setOfferDate(LocalDate offerDate) { this.offerDate = offerDate; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
}
