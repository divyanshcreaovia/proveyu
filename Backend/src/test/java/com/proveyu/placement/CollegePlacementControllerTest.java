package com.proveyu.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.placement.application.PlacementDriveService;
import com.proveyu.placement.application.dto.PlacementDriveDto;
import com.proveyu.placement.web.CollegePlacementController;
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
public class CollegePlacementControllerTest {

    @Mock
    private PlacementDriveService driveService;

    @InjectMocks
    private CollegePlacementController collegePlacementController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(collegePlacementController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        currentUserId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(currentUserId.toString(), null, Collections.emptyList());
    }

    @Test
    @DisplayName("GET /api/v1/college/placements - List all placement drives")
    void testGetDrives() throws Exception {
        PlacementDriveDto.Response response = new PlacementDriveDto.Response();
        response.setId(UUID.randomUUID());
        response.setCompanyName("TCS");
        response.setRoles("System Engineer");
        response.setPackageLpa(7.0);

        when(driveService.getDrives(eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/college/placements")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].companyName").value("TCS"))
                .andExpect(jsonPath("$.data[0].roles").value("System Engineer"));
    }

    @Test
    @DisplayName("GET /api/v1/college/placements/{id} - Get placement drive by ID")
    void testGetDriveById() throws Exception {
        UUID driveId = UUID.randomUUID();
        PlacementDriveDto.Response response = new PlacementDriveDto.Response();
        response.setId(driveId);
        response.setCompanyName("Infosys");
        response.setRoles("Specialist Programmer");
        response.setPackageLpa(9.5);

        when(driveService.getDriveById(eq(currentUserId), eq(driveId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/college/placements/" + driveId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("Infosys"))
                .andExpect(jsonPath("$.data.packageLpa").value(9.5));
    }

    @Test
    @DisplayName("POST /api/v1/college/placements - Create placement drive")
    void testCreateDrive() throws Exception {
        PlacementDriveDto.Request request = new PlacementDriveDto.Request();
        request.setCompanyName("Accenture");
        request.setRoles("Associate Software Engineer");
        request.setPackageLpa(4.5);
        request.setDriveDate("2026-11-15");

        PlacementDriveDto.Response response = new PlacementDriveDto.Response();
        response.setId(UUID.randomUUID());
        response.setCompanyName("Accenture");
        response.setRoles("Associate Software Engineer");
        response.setPackageLpa(4.5);

        when(driveService.createDrive(eq(currentUserId), any(PlacementDriveDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/college/placements")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("Accenture"));
    }

    @Test
    @DisplayName("PUT /api/v1/college/placements/{id} - Update placement drive")
    void testUpdateDrive() throws Exception {
        UUID driveId = UUID.randomUUID();
        PlacementDriveDto.Request request = new PlacementDriveDto.Request();
        request.setCompanyName("Accenture Updated");
        request.setRoles("Software Engineer");
        request.setPackageLpa(6.5);

        PlacementDriveDto.Response response = new PlacementDriveDto.Response();
        response.setId(driveId);
        response.setCompanyName("Accenture Updated");
        response.setRoles("Software Engineer");
        response.setPackageLpa(6.5);

        when(driveService.updateDrive(eq(currentUserId), eq(driveId), any(PlacementDriveDto.Request.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/college/placements/" + driveId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("Accenture Updated"));
    }

    @Test
    @DisplayName("DELETE /api/v1/college/placements/{id} - Delete placement drive")
    void testDeleteDrive() throws Exception {
        UUID driveId = UUID.randomUUID();
        doNothing().when(driveService).deleteDrive(eq(currentUserId), eq(driveId));

        mockMvc.perform(delete("/api/v1/college/placements/" + driveId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Placement drive deleted successfully"));
    }
}
