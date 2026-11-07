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
@Table(name = "college_custom_roles")
public class CollegeCustomRole {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "organization_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID organizationId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(length = 255)
    private String description;

    @Column(name = "assigned_branch", nullable = false, length = 100)
    private String assignedBranch = "ALL";

    @Column(name = "can_view_students", nullable = false)
    private boolean canViewStudents = true;

    @Column(name = "can_edit_students", nullable = false)
    private boolean canEditStudents = true;

    @Column(name = "can_manage_placements", nullable = false)
    private boolean canManagePlacements = false;

    @Column(name = "can_invite_team", nullable = false)
    private boolean canInviteTeam = false;

    @Column(name = "can_export_reports", nullable = false)
    private boolean canExportReports = true;

    @Column(name = "access_percentage", length = 30)
    private String accessPercentage = "80% Access";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollegeCustomRole() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAssignedBranch() { return assignedBranch; }
    public void setAssignedBranch(String assignedBranch) { this.assignedBranch = assignedBranch; }

    public boolean isCanViewStudents() { return canViewStudents; }
    public void setCanViewStudents(boolean canViewStudents) { this.canViewStudents = canViewStudents; }

    public boolean isCanEditStudents() { return canEditStudents; }
    public void setCanEditStudents(boolean canEditStudents) { this.canEditStudents = canEditStudents; }

    public boolean isCanManagePlacements() { return canManagePlacements; }
    public void setCanManagePlacements(boolean canManagePlacements) { this.canManagePlacements = canManagePlacements; }

    public boolean isCanInviteTeam() { return canInviteTeam; }
    public void setCanInviteTeam(boolean canInviteTeam) { this.canInviteTeam = canInviteTeam; }

    public boolean isCanExportReports() { return canExportReports; }
    public void setCanExportReports(boolean canExportReports) { this.canExportReports = canExportReports; }

    public String getAccessPercentage() { return accessPercentage; }
    public void setAccessPercentage(String accessPercentage) { this.accessPercentage = accessPercentage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
