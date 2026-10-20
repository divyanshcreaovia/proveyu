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
@Table(name = "college_team_members")
public class CollegeTeamMember {

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

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 25)
    private String phone;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "custom_role_id", columnDefinition = "CHAR(36)")
    private UUID customRoleId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName = "Custom Role";

    @Column(name = "assigned_branch", nullable = false, length = 100)
    private String assignedBranch = "ALL";

    @Column(length = 150)
    private String designation;

    @Column(name = "access_level", length = 50)
    private String accessLevel = "80% Access";

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, PENDING_INVITE, INACTIVE

    @Column(name = "last_active", length = 100)
    private String lastActive = "Recently";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollegeTeamMember() {}

    public CollegeTeamMember(UUID id, UUID organizationId, UUID userId, String name, String email, String phone,
                             UUID customRoleId, String roleName, String assignedBranch, String designation,
                             String accessLevel, String status, String lastActive, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.customRoleId = customRoleId;
        this.roleName = roleName != null ? roleName : "Custom Role";
        this.assignedBranch = assignedBranch != null ? assignedBranch : "ALL";
        this.designation = designation;
        this.accessLevel = accessLevel != null ? accessLevel : "80% Access";
        this.status = status != null ? status : "ACTIVE";
        this.lastActive = lastActive != null ? lastActive : "Recently";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UUID getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(UUID customRoleId) { this.customRoleId = customRoleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getAssignedBranch() { return assignedBranch; }
    public void setAssignedBranch(String assignedBranch) { this.assignedBranch = assignedBranch; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLastActive() { return lastActive; }
    public void setLastActive(String lastActive) { this.lastActive = lastActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static CollegeTeamMemberBuilder builder() { return new CollegeTeamMemberBuilder(); }

    public static class CollegeTeamMemberBuilder {
        private UUID id;
        private UUID organizationId;
        private UUID userId;
        private String name;
        private String email;
        private String phone;
        private UUID customRoleId;
        private String roleName = "Custom Role";
        private String assignedBranch = "ALL";
        private String designation;
        private String accessLevel = "80% Access";
        private String status = "ACTIVE";
        private String lastActive = "Recently";
        private Instant createdAt;
        private Instant updatedAt;

        public CollegeTeamMemberBuilder id(UUID id) { this.id = id; return this; }
        public CollegeTeamMemberBuilder organizationId(UUID organizationId) { this.organizationId = organizationId; return this; }
        public CollegeTeamMemberBuilder userId(UUID userId) { this.userId = userId; return this; }
        public CollegeTeamMemberBuilder name(String name) { this.name = name; return this; }
        public CollegeTeamMemberBuilder email(String email) { this.email = email; return this; }
        public CollegeTeamMemberBuilder phone(String phone) { this.phone = phone; return this; }
        public CollegeTeamMemberBuilder customRoleId(UUID customRoleId) { this.customRoleId = customRoleId; return this; }
        public CollegeTeamMemberBuilder roleName(String roleName) { this.roleName = roleName; return this; }
        public CollegeTeamMemberBuilder assignedBranch(String assignedBranch) { this.assignedBranch = assignedBranch; return this; }
        public CollegeTeamMemberBuilder designation(String designation) { this.designation = designation; return this; }
        public CollegeTeamMemberBuilder accessLevel(String accessLevel) { this.accessLevel = accessLevel; return this; }
        public CollegeTeamMemberBuilder status(String status) { this.status = status; return this; }
        public CollegeTeamMemberBuilder lastActive(String lastActive) { this.lastActive = lastActive; return this; }
        public CollegeTeamMemberBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public CollegeTeamMemberBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public CollegeTeamMember build() {
            return new CollegeTeamMember(id, organizationId, userId, name, email, phone, customRoleId, roleName, assignedBranch, designation, accessLevel, status, lastActive, createdAt, updatedAt);
        }
    }
}
