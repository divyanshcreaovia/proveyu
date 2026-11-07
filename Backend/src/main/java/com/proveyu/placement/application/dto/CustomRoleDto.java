package com.proveyu.placement.application.dto;

import com.proveyu.placement.domain.CollegeCustomRole;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class CustomRoleDto {

    public static class Request {
        @NotBlank(message = "Role name is required")
        private String roleName;

        private String description;
        private String assignedBranch;
        private Boolean canViewStudents;
        private Boolean canEditStudents;
        private Boolean canManagePlacements;
        private Boolean canInviteTeam;
        private Boolean canExportReports;
        private String accessPercentage;

        public Request() {}

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAssignedBranch() { return assignedBranch; }
        public void setAssignedBranch(String assignedBranch) { this.assignedBranch = assignedBranch; }

        public Boolean getCanViewStudents() { return canViewStudents; }
        public void setCanViewStudents(Boolean canViewStudents) { this.canViewStudents = canViewStudents; }

        public Boolean getCanEditStudents() { return canEditStudents; }
        public void setCanEditStudents(Boolean canEditStudents) { this.canEditStudents = canEditStudents; }

        public Boolean getCanManagePlacements() { return canManagePlacements; }
        public void setCanManagePlacements(Boolean canManagePlacements) { this.canManagePlacements = canManagePlacements; }

        public Boolean getCanInviteTeam() { return canInviteTeam; }
        public void setCanInviteTeam(Boolean canInviteTeam) { this.canInviteTeam = canInviteTeam; }

        public Boolean getCanExportReports() { return canExportReports; }
        public void setCanExportReports(Boolean canExportReports) { this.canExportReports = canExportReports; }

        public String getAccessPercentage() { return accessPercentage; }
        public void setAccessPercentage(String accessPercentage) { this.accessPercentage = accessPercentage; }
    }

    public static class Response {
        private UUID id;
        private UUID organizationId;
        private String roleName;
        private String description;
        private String assignedBranch;
        private boolean canViewStudents;
        private boolean canEditStudents;
        private boolean canManagePlacements;
        private boolean canInviteTeam;
        private boolean canExportReports;
        private String accessPercentage;

        public Response() {}

        public static Response fromEntity(CollegeCustomRole role) {
            if (role == null) return null;
            Response r = new Response();
            r.setId(role.getId());
            r.setOrganizationId(role.getOrganizationId());
            r.setRoleName(role.getRoleName());
            r.setDescription(role.getDescription());
            r.setAssignedBranch(role.getAssignedBranch());
            r.setCanViewStudents(role.isCanViewStudents());
            r.setCanEditStudents(role.isCanEditStudents());
            r.setCanManagePlacements(role.isCanManagePlacements());
            r.setCanInviteTeam(role.isCanInviteTeam());
            r.setCanExportReports(role.isCanExportReports());
            r.setAccessPercentage(role.getAccessPercentage());
            return r;
        }

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
    }
}
