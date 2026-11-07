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
@Table(name = "placement_records")
public class PlacementRecord {

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
    @Column(name = "student_profile_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID studentProfileId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "role_offered", nullable = false, length = 150)
    private String roleOffered;

    @Column(nullable = false, length = 50)
    private String ctc;

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

    @Column(nullable = false, length = 50)
    private String status = "OFFERED"; // OFFERED, ACCEPTED, REJECTED, IN_PROCESS

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlacementRecord() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(UUID studentProfileId) { this.studentProfileId = studentProfileId; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRoleOffered() { return roleOffered; }
    public void setRoleOffered(String roleOffered) { this.roleOffered = roleOffered; }

    public String getCtc() { return ctc; }
    public void setCtc(String ctc) { this.ctc = ctc; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
