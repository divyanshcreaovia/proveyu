package com.proveyu.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.placement.application.CollegeTeamService;
import com.proveyu.placement.application.dto.TeamMemberDto;
import com.proveyu.placement.web.CollegeTeamController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class CollegeTeamControllerTest {

    @Mock
    private CollegeTeamService teamService;

    @InjectMocks
    private CollegeTeamController collegeTeamController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(collegeTeamController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        currentUserId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(currentUserId.toString(), null, Collections.emptyList());
    }

    @Test
    @DisplayName("GET /api/v1/college/team/context - Resolve logged-in user context")
    void testGetContext() throws Exception {
        com.proveyu.placement.application.PlacementManagementService.OrgUserContext ctx =
                new com.proveyu.placement.application.PlacementManagementService.OrgUserContext();
        ctx.organizationId = UUID.randomUUID();
        ctx.roleName = "Department Coordinator";
        ctx.assignedBranch = "Information Technology";
        ctx.isHeadTpo = false;
        ctx.canViewStudents = true;
        ctx.canEditStudents = true;
        ctx.canManagePlacements = false;
        ctx.canInviteTeam = false;

        when(teamService.getCurrentUserContext(eq(currentUserId))).thenReturn(ctx);

        mockMvc.perform(get("/api/v1/college/team/context")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("Department Coordinator"))
                .andExpect(jsonPath("$.data.assignedBranch").value("Information Technology"))
                .andExpect(jsonPath("$.data.isHeadTpo").value(false))
                .andExpect(jsonPath("$.data.canInviteTeam").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/college/team - List team members")
    void testGetTeamMembers() throws Exception {
        TeamMemberDto.Response member = new TeamMemberDto.Response();
        member.setId(UUID.randomUUID());
        member.setName("Prof. Rajesh Gupta");
        member.setEmail("rajesh@college.edu");
        member.setRoleName("Placement Coordinator");
        member.setAssignedBranch("Computer Science & Engineering");

        when(teamService.getTeamMembers(eq(currentUserId))).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/college/team")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Prof. Rajesh Gupta"))
                .andExpect(jsonPath("$.data[0].roleName").value("Placement Coordinator"));
    }

    @Test
    @DisplayName("POST /api/v1/college/team/invite - Invite new team member")
    void testInviteMember() throws Exception {
        TeamMemberDto.InviteRequest request = new TeamMemberDto.InviteRequest();
        request.setName("Dr. Sunil Mehta");
        request.setEmail("sunil@college.edu");
        request.setAssignedBranch("Information Technology");
        request.setRoleName("Department Coordinator");

        TeamMemberDto.Response response = new TeamMemberDto.Response();
        response.setId(UUID.randomUUID());
        response.setName("Dr. Sunil Mehta");
        response.setEmail("sunil@college.edu");
        response.setRoleName("Department Coordinator");

        when(teamService.inviteOrAddTeamMember(eq(currentUserId), any(TeamMemberDto.InviteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/college/team/invite")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Dr. Sunil Mehta"));
    }

    @Test
    @DisplayName("PUT /api/v1/college/team/{id} - Update team member")
    void testUpdateMember() throws Exception {
        UUID memberId = UUID.randomUUID();
        TeamMemberDto.UpdateRequest request = new TeamMemberDto.UpdateRequest();
        request.setName("Dr. Sunil Mehta Updated");

        TeamMemberDto.Response response = new TeamMemberDto.Response();
        response.setId(memberId);
        response.setName("Dr. Sunil Mehta Updated");

        when(teamService.updateTeamMember(eq(currentUserId), eq(memberId), any(TeamMemberDto.UpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/college/team/" + memberId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Dr. Sunil Mehta Updated"));
    }

    @Test
    @DisplayName("DELETE /api/v1/college/team/{id} - Remove team member")
    void testDeleteMember() throws Exception {
        UUID memberId = UUID.randomUUID();
        doNothing().when(teamService).deleteTeamMember(eq(currentUserId), eq(memberId));

        mockMvc.perform(delete("/api/v1/college/team/" + memberId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Team member removed successfully"));
    }
}
