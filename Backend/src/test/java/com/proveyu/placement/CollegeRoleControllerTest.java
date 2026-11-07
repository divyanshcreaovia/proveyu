package com.proveyu.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.placement.application.CollegeTeamService;
import com.proveyu.placement.application.dto.CustomRoleDto;
import com.proveyu.placement.web.CollegeRoleController;
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
public class CollegeRoleControllerTest {

    @Mock
    private CollegeTeamService teamService;

    @InjectMocks
    private CollegeRoleController collegeRoleController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(collegeRoleController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        currentUserId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(currentUserId.toString(), null, Collections.emptyList());
    }

    @Test
    @DisplayName("GET /api/v1/college/roles - List all custom roles")
    void testGetCustomRoles() throws Exception {
        CustomRoleDto.Response response = new CustomRoleDto.Response();
        response.setId(UUID.randomUUID());
        response.setRoleName("Department Placement Coordinator");
        response.setAssignedBranch("Computer Science & Engineering");

        when(teamService.getCustomRoles(eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/college/roles")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].roleName").value("Department Placement Coordinator"));
    }

    @Test
    @DisplayName("GET /api/v1/college/roles/{id} - Get custom role by ID")
    void testGetCustomRoleById() throws Exception {
        UUID roleId = UUID.randomUUID();
        CustomRoleDto.Response response = new CustomRoleDto.Response();
        response.setId(roleId);
        response.setRoleName("Student Placement Representative");

        when(teamService.getCustomRoleById(eq(currentUserId), eq(roleId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/college/roles/" + roleId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("Student Placement Representative"));
    }

    @Test
    @DisplayName("POST /api/v1/college/roles - Create custom role")
    void testCreateCustomRole() throws Exception {
        CustomRoleDto.Request request = new CustomRoleDto.Request();
        request.setRoleName("Head Coordinator");
        request.setAssignedBranch("ALL");
        request.setCanManagePlacements(true);

        CustomRoleDto.Response response = new CustomRoleDto.Response();
        response.setId(UUID.randomUUID());
        response.setRoleName("Head Coordinator");
        response.setAssignedBranch("ALL");

        when(teamService.createCustomRole(eq(currentUserId), any(CustomRoleDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/college/roles")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("Head Coordinator"));
    }

    @Test
    @DisplayName("PUT /api/v1/college/roles/{id} - Update custom role")
    void testUpdateCustomRole() throws Exception {
        UUID roleId = UUID.randomUUID();
        CustomRoleDto.Request request = new CustomRoleDto.Request();
        request.setRoleName("Head Coordinator Updated");

        CustomRoleDto.Response response = new CustomRoleDto.Response();
        response.setId(roleId);
        response.setRoleName("Head Coordinator Updated");

        when(teamService.updateCustomRole(eq(currentUserId), eq(roleId), any(CustomRoleDto.Request.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/college/roles/" + roleId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("Head Coordinator Updated"));
    }

    @Test
    @DisplayName("DELETE /api/v1/college/roles/{id} - Delete custom role")
    void testDeleteCustomRole() throws Exception {
        UUID roleId = UUID.randomUUID();
        doNothing().when(teamService).deleteCustomRole(eq(currentUserId), eq(roleId));

        mockMvc.perform(delete("/api/v1/college/roles/" + roleId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Custom role deleted successfully"));
    }
}
