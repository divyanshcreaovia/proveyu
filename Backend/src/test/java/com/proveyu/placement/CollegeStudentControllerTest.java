package com.proveyu.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.placement.application.PlacementManagementService;
import com.proveyu.placement.application.dto.BulkPlacementRequestDto;
import com.proveyu.placement.application.dto.BulkUploadResultDto;
import com.proveyu.placement.application.dto.CollegeDashboardDto;
import com.proveyu.placement.application.dto.StudentProfileDto;
import com.proveyu.placement.web.CollegeStudentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
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
public class CollegeStudentControllerTest {

    @Mock
    private PlacementManagementService placementManagementService;

    @InjectMocks
    private CollegeStudentController collegeStudentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(collegeStudentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        currentUserId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(currentUserId.toString(), null, Collections.emptyList());
    }

    @Test
    @DisplayName("GET /api/v1/college/students - Get paginated students successfully")
    void testGetStudents() throws Exception {
        StudentProfileDto dto = new StudentProfileDto();
        dto.setId(UUID.randomUUID());
        dto.setRollNumber("2024-CSE-001");
        dto.setFirstName("Rahul");
        dto.setLastName("Sharma");
        dto.setBranch("Computer Science & Engineering");

        when(placementManagementService.getStudents(
                eq(currentUserId), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/college/students")
                        .principal(authentication)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].rollNumber").value("2024-CSE-001"))
                .andExpect(jsonPath("$.data.content[0].firstName").value("Rahul"));
    }

    @Test
    @DisplayName("GET /api/v1/college/students/{id} - Get student profile by ID")
    void testGetStudentById() throws Exception {
        UUID studentId = UUID.randomUUID();
        StudentProfileDto dto = new StudentProfileDto();
        dto.setId(studentId);
        dto.setRollNumber("2024-CSE-002");
        dto.setFirstName("Priya");
        dto.setLastName("Verma");

        when(placementManagementService.getStudentById(eq(currentUserId), eq(studentId))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/college/students/" + studentId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rollNumber").value("2024-CSE-002"))
                .andExpect(jsonPath("$.data.firstName").value("Priya"));
    }

    @Test
    @DisplayName("POST /api/v1/college/students - Create new student manually")
    void testCreateStudent() throws Exception {
        StudentProfileDto requestDto = new StudentProfileDto();
        requestDto.setRollNumber("2024-IT-005");
        requestDto.setFirstName("Ankit");
        requestDto.setEmail("ankit@example.com");
        requestDto.setBranch("Information Technology");

        StudentProfileDto createdDto = new StudentProfileDto();
        createdDto.setId(UUID.randomUUID());
        createdDto.setRollNumber("2024-IT-005");
        createdDto.setFirstName("Ankit");
        createdDto.setEmail("ankit@example.com");
        createdDto.setBranch("Information Technology");

        when(placementManagementService.createStudent(eq(currentUserId), any(StudentProfileDto.class)))
                .thenReturn(createdDto);

        mockMvc.perform(post("/api/v1/college/students")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rollNumber").value("2024-IT-005"));
    }

    @Test
    @DisplayName("PUT /api/v1/college/students/{id} - Update student profile")
    void testUpdateStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        StudentProfileDto requestDto = new StudentProfileDto();
        requestDto.setRollNumber("2024-CSE-001");
        requestDto.setFirstName("Rahul Updated");

        when(placementManagementService.updateStudent(eq(currentUserId), eq(studentId), any(StudentProfileDto.class)))
                .thenReturn(requestDto);

        mockMvc.perform(put("/api/v1/college/students/" + studentId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Rahul Updated"));
    }

    @Test
    @DisplayName("POST /api/v1/college/students/bulk-upload - Upload CSV roster")
    void testBulkUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test_roster.csv", "text/csv", "Roll Number,First Name\n2024-01,Test".getBytes(StandardCharsets.UTF_8)
        );

        BulkUploadResultDto resultDto = BulkUploadResultDto.builder()
                .totalRows(1)
                .importedCount(1)
                .failedCount(0)
                .errors(Collections.emptyList())
                .importedStudents(Collections.emptyList())
                .build();

        when(placementManagementService.bulkUploadStudents(eq(currentUserId), any())).thenReturn(resultDto);

        mockMvc.perform(multipart("/api/v1/college/students/bulk-upload")
                        .file(file)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importedCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/college/students/template - Download blank CSV template")
    void testDownloadTemplate() throws Exception {
        byte[] csv = "Roll Number,First Name,Last Name\n".getBytes(StandardCharsets.UTF_8);
        when(placementManagementService.downloadTemplate(true)).thenReturn(csv);

        mockMvc.perform(get("/api/v1/college/students/template").param("sample", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"proveyu_student_import_sample_template.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"));
    }

    @Test
    @DisplayName("POST /api/v1/college/students/bulk-placement - Record bulk placement updates")
    void testBulkPlacement() throws Exception {
        BulkPlacementRequestDto request = new BulkPlacementRequestDto();
        request.setStudentIds(List.of(UUID.randomUUID()));
        request.setCompany("Google");
        request.setRole("Software Engineer");
        request.setCtc("24 LPA");

        doNothing().when(placementManagementService).bulkUpdatePlacement(eq(currentUserId), any(BulkPlacementRequestDto.class));

        mockMvc.perform(post("/api/v1/college/students/bulk-placement")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Successfully updated placement status for selected candidates"));
    }

    @Test
    @DisplayName("GET /api/v1/college/students/dashboard/kpi - Fetch KPI Dashboard")
    void testGetDashboardKpi() throws Exception {
        CollegeDashboardDto kpi = CollegeDashboardDto.builder()
                .totalStudents(500)
                .placedStudents(350)
                .unplacedStudents(150)
                .placementPercentage(70.0)
                .activeDrives(8)
                .averagePackageLpa(7.5)
                .highestPackageLpa(22.0)
                .build();

        when(placementManagementService.getDashboardKpi(eq(currentUserId))).thenReturn(kpi);

        mockMvc.perform(get("/api/v1/college/students/dashboard/kpi")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalStudents").value(500))
                .andExpect(jsonPath("$.data.placedStudents").value(350))
                .andExpect(jsonPath("$.data.placementPercentage").value(70.0));
    }
}
