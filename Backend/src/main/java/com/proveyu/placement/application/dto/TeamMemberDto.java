package com.proveyu.placement.application.dto;

import com.proveyu.placement.domain.CollegeTeamMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class TeamMemberDto {

    public static class Response {
        private UUID id;
        private UUID organizationId;
        private UUID userId;
        private UUID customRoleId;
        private String name;
        private String email;
        private String phone;
        private String roleName;
        private String assignedBranch;
        private String designation;
        private String accessLevel;
        private String status;
        private String lastActive;

        public Response() {}

        public static Response fromEntity(CollegeTeamMember member) {
            if (member == null) return null;
            Response r = new Response();
            r.setId(member.getId());
            r.setOrganizationId(member.getOrganizationId());
            r.setUserId(member.getUserId());
            r.setCustomRoleId(member.getCustomRoleId());
            r.setName(member.getName());
            r.setEmail(member.getEmail());
            r.setPhone(member.getPhone());
            r.setRoleName(member.getRoleName() != null ? member.getRoleName() : "Custom Role");
            r.setAssignedBranch(member.getAssignedBranch());
            r.setDesignation(member.getDesignation());
            r.setAccessLevel(member.getAccessLevel());
            r.setStatus(member.getStatus());
            r.setLastActive(member.getLastActive());
            return r;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }

        public UUID getCustomRoleId() { return customRoleId; }
        public void setCustomRoleId(UUID customRoleId) { this.customRoleId = customRoleId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

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
    }

    public static class InviteRequest {
        @NotBlank(message = "Member name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        private String email;

        private String phone;

        // Custom role reference or custom role title
        private UUID customRoleId;
        private String roleName;

        @NotBlank(message = "Assigned branch is required (e.g. Computer Science & Engineering, or ALL)")
        private String assignedBranch;

        private String designation;
        private String accessLevel;
        private String tempPassword;

        public InviteRequest() {}

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

        public String getTempPassword() { return tempPassword; }
        public void setTempPassword(String tempPassword) { this.tempPassword = tempPassword; }
    }

    public static class UpdateRequest {
        private String name;
        private String phone;
        private UUID customRoleId;
        private String roleName;
        private String assignedBranch;
        private String designation;
        private String accessLevel;
        private String status;

        public UpdateRequest() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

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
    }
}
