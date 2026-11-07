package com.proveyu.placement.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.placement.application.dto.CustomRoleDto;
import com.proveyu.placement.application.dto.TeamMemberDto;
import com.proveyu.placement.domain.CollegeCustomRole;
import com.proveyu.placement.domain.CollegeTeamMember;
import com.proveyu.placement.infrastructure.CollegeCustomRoleRepository;
import com.proveyu.placement.infrastructure.CollegeTeamMemberRepository;
import com.proveyu.shared.error.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CollegeTeamService {

    private static final Logger log = LoggerFactory.getLogger(CollegeTeamService.class);

    private final CollegeTeamMemberRepository teamMemberRepository;
    private final CollegeCustomRoleRepository customRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlacementManagementService placementManagementService;

    public CollegeTeamService(CollegeTeamMemberRepository teamMemberRepository,
                              CollegeCustomRoleRepository customRoleRepository,
                              OrganizationRepository organizationRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              PlacementManagementService placementManagementService) {
        this.teamMemberRepository = teamMemberRepository;
        this.customRoleRepository = customRoleRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.placementManagementService = placementManagementService;
    }

    // ================= USER CONTEXT =================

    @Transactional(readOnly = true)
    public PlacementManagementService.OrgUserContext getCurrentUserContext(UUID currentUserId) {
        return placementManagementService.resolveContext(currentUserId);
    }

    // ================= CUSTOM ROLES MANAGEMENT =================

    @Transactional
    public CustomRoleDto.Response createCustomRole(UUID currentUserId, CustomRoleDto.Request request) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can create custom roles", HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        if (customRoleRepository.existsByOrganizationIdAndRoleName(ctx.organizationId, request.getRoleName())) {
            throw new DomainException("Custom role with name '" + request.getRoleName() + "' already exists", HttpStatus.BAD_REQUEST, "ROLE_EXISTS");
        }

        CollegeCustomRole role = new CollegeCustomRole();
        role.setOrganizationId(ctx.organizationId);
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setAssignedBranch(request.getAssignedBranch() != null ? request.getAssignedBranch() : "ALL");
        role.setCanViewStudents(request.getCanViewStudents() == null || request.getCanViewStudents());
        role.setCanEditStudents(request.getCanEditStudents() == null || request.getCanEditStudents());
        role.setCanManagePlacements(Boolean.TRUE.equals(request.getCanManagePlacements()));
        role.setCanInviteTeam(Boolean.TRUE.equals(request.getCanInviteTeam()));
        role.setCanExportReports(request.getCanExportReports() == null || request.getCanExportReports());
        role.setAccessPercentage(request.getAccessPercentage() != null ? request.getAccessPercentage() : "80% Access");

        CollegeCustomRole saved = customRoleRepository.save(role);
        log.info("[CUSTOM ROLE CREATED] Created custom role: id=[{}] name=[{}] branch=[{}]", saved.getId(), saved.getRoleName(), saved.getAssignedBranch());

        return CustomRoleDto.Response.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomRoleDto.Response> getCustomRoles(UUID currentUserId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        return customRoleRepository.findByOrganizationId(ctx.organizationId)
                .stream().map(CustomRoleDto.Response::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public CustomRoleDto.Response getCustomRoleById(UUID currentUserId, UUID roleId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        CollegeCustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new DomainException("Custom role not found", HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND"));

        if (!role.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Role does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        return CustomRoleDto.Response.fromEntity(role);
    }

    @Transactional
    public CustomRoleDto.Response updateCustomRole(UUID currentUserId, UUID roleId, CustomRoleDto.Request request) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can update custom roles", HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        CollegeCustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new DomainException("Custom role not found", HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND"));

        if (!role.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Role does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (request.getRoleName() != null) role.setRoleName(request.getRoleName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getAssignedBranch() != null) role.setAssignedBranch(request.getAssignedBranch());
        if (request.getCanViewStudents() != null) role.setCanViewStudents(request.getCanViewStudents());
        if (request.getCanEditStudents() != null) role.setCanEditStudents(request.getCanEditStudents());
        if (request.getCanManagePlacements() != null) role.setCanManagePlacements(request.getCanManagePlacements());
        if (request.getCanInviteTeam() != null) role.setCanInviteTeam(request.getCanInviteTeam());
        if (request.getCanExportReports() != null) role.setCanExportReports(request.getCanExportReports());
        if (request.getAccessPercentage() != null) role.setAccessPercentage(request.getAccessPercentage());

        CollegeCustomRole saved = customRoleRepository.save(role);
        return CustomRoleDto.Response.fromEntity(saved);
    }

    @Transactional
    public void deleteCustomRole(UUID currentUserId, UUID roleId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can delete custom roles", HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        CollegeCustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new DomainException("Custom role not found", HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND"));

        if (!role.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Role does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        customRoleRepository.delete(role);
    }

    // ================= TEAM MEMBERS MANAGEMENT =================

    @Transactional(readOnly = true)
    public List<TeamMemberDto.Response> getTeamMembers(UUID currentUserId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        List<CollegeTeamMember> members = teamMemberRepository.findByOrganizationId(ctx.organizationId);

        if (members.isEmpty()) {
            Organization org = organizationRepository.findById(ctx.organizationId).orElse(null);
            if (org != null) {
                User orgUser = userRepository.findById(org.getUserId()).orElse(null);
                if (orgUser != null) {
                    CollegeTeamMember adminMember = CollegeTeamMember.builder()
                            .organizationId(org.getId())
                            .userId(orgUser.getId())
                            .name(orgUser.getFullName())
                            .email(orgUser.getEmail())
                            .phone(orgUser.getPhone())
                            .roleName("Institution Admin")
                            .assignedBranch("ALL")
                            .designation("Head Placement Admin")
                            .accessLevel("Full Admin Access")
                            .status("ACTIVE")
                            .lastActive("Now")
                            .build();
                    teamMemberRepository.save(adminMember);
                    members = List.of(adminMember);
                }
            }
        }

        return members.stream().map(TeamMemberDto.Response::fromEntity).toList();
    }

    @Transactional
    public TeamMemberDto.Response inviteOrAddTeamMember(UUID currentUserId, TeamMemberDto.InviteRequest request) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can invite and manage team members",
                    HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        if (teamMemberRepository.existsByOrganizationIdAndEmail(ctx.organizationId, request.getEmail())) {
            throw new DomainException("A team member with email " + request.getEmail() + " already exists in this college",
                    HttpStatus.BAD_REQUEST, "MEMBER_EXISTS");
        }

        String displayRoleName = "Custom Role";
        String effectiveBranch = request.getAssignedBranch() != null ? request.getAssignedBranch() : "ALL";
        String accessLevel = request.getAccessLevel() != null ? request.getAccessLevel() : "80% Access";
        UUID customRoleId = request.getCustomRoleId();

        // If customRoleId is provided, fetch details from CollegeCustomRole
        if (customRoleId != null) {
            CollegeCustomRole cr = customRoleRepository.findById(customRoleId)
                    .orElseThrow(() -> new DomainException("Custom role not found", HttpStatus.BAD_REQUEST, "CUSTOM_ROLE_NOT_FOUND"));
            displayRoleName = cr.getRoleName();
            if (cr.getAssignedBranch() != null && !"ALL".equalsIgnoreCase(cr.getAssignedBranch())) {
                effectiveBranch = cr.getAssignedBranch();
            }
            if (cr.getAccessPercentage() != null) {
                accessLevel = cr.getAccessPercentage();
            }
        } else if (request.getRoleName() != null && !request.getRoleName().trim().isEmpty()) {
            displayRoleName = request.getRoleName().trim();
        }

        String rawPassword = request.getTempPassword() != null ? request.getTempPassword() : "College@" + System.currentTimeMillis();

        User user = userRepository.findByEmail(request.getEmail()).orElseGet(() -> {
            User newUser = User.builder()
                    .email(request.getEmail())
                    .fullName(request.getName())
                    .phone(request.getPhone())
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(Role.ORGANIZATION)
                    .active(true)
                    .build();
            return userRepository.save(newUser);
        });

        CollegeTeamMember member = CollegeTeamMember.builder()
                .organizationId(ctx.organizationId)
                .userId(user.getId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .customRoleId(customRoleId)
                .roleName(displayRoleName)
                .assignedBranch(effectiveBranch)
                .designation(request.getDesignation() != null ? request.getDesignation() : displayRoleName)
                .accessLevel(accessLevel)
                .status("ACTIVE")
                .lastActive("Invitation Sent")
                .build();

        CollegeTeamMember saved = teamMemberRepository.save(member);
        log.info("[TEAM MEMBER CREATED] New college team member: name=[{}] email=[{}] roleName=[{}] branch=[{}]",
                saved.getName(), saved.getEmail(), saved.getRoleName(), saved.getAssignedBranch());

        return TeamMemberDto.Response.fromEntity(saved);
    }

    @Transactional
    public TeamMemberDto.Response updateTeamMember(UUID currentUserId, UUID memberId, TeamMemberDto.UpdateRequest request) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can modify team member privileges",
                    HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        CollegeTeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("Team member not found", HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));

        if (!member.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Member does not belong to your college", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (request.getName() != null) member.setName(request.getName());
        if (request.getPhone() != null) member.setPhone(request.getPhone());
        if (request.getCustomRoleId() != null) member.setCustomRoleId(request.getCustomRoleId());
        if (request.getRoleName() != null) member.setRoleName(request.getRoleName());
        if (request.getAssignedBranch() != null) member.setAssignedBranch(request.getAssignedBranch());
        if (request.getDesignation() != null) member.setDesignation(request.getDesignation());
        if (request.getAccessLevel() != null) member.setAccessLevel(request.getAccessLevel());
        if (request.getStatus() != null) member.setStatus(request.getStatus());

        CollegeTeamMember saved = teamMemberRepository.save(member);
        return TeamMemberDto.Response.fromEntity(saved);
    }

    @Transactional
    public void deleteTeamMember(UUID currentUserId, UUID memberId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        if (!ctx.isHeadTpo) {
            throw new DomainException("Only College Admin / TPO can delete team members",
                    HttpStatus.FORBIDDEN, "TPO_PERMISSION_REQUIRED");
        }

        CollegeTeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("Team member not found", HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));

        if (!member.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Member does not belong to your college", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        teamMemberRepository.delete(member);
    }
}
