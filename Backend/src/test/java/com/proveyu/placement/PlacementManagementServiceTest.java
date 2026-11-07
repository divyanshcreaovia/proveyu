package com.proveyu.placement;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.organization.domain.OrgType;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.infrastructure.OrganizationCandidateRepository;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.placement.application.PlacementManagementService;
import com.proveyu.placement.application.dto.BulkUploadResultDto;
import com.proveyu.placement.application.dto.StudentProfileDto;
import com.proveyu.placement.domain.CollegeTeamMember;
import com.proveyu.placement.domain.StudentProfile;
import com.proveyu.placement.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlacementManagementServiceTest {

    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private CollegeTeamMemberRepository teamMemberRepository;
    @Mock
    private CollegeCustomRoleRepository customRoleRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationCandidateRepository organizationCandidateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlacementRecordRepository placementRecordRepository;
    @Mock
    private PlacementDriveRepository1 placementDriveRepository;
    @Spy
    private ExcelStudentParser excelStudentParser = new ExcelStudentParser();
    @Spy
    private StudentTemplateGenerator templateGenerator = new StudentTemplateGenerator();
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PlacementManagementService service;

    private UUID tpoUserId;
    private UUID hodUserId;
    private UUID orgId;
    private Organization organization;

    @BeforeEach
    void setUp() {
        tpoUserId = UUID.randomUUID();
        hodUserId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        organization = Organization.builder()
                .id(orgId)
                .userId(tpoUserId)
                .legalName("MGM College of Engineering & Technology")
                .orgType(OrgType.COLLEGE)
                .build();
    }

    @Test
    void testTemplateGeneration() {
        byte[] csv = service.downloadTemplate(true);
        assertNotNull(csv);
        String content = new String(csv, StandardCharsets.UTF_8);
        assertTrue(content.contains("Roll Number"));
        assertTrue(content.contains("2024-CSE-001"));
        assertTrue(content.contains("Computer Science & Engineering"));
    }

    @Test
    void testBulkUploadStudentsSuccess() {
        when(organizationRepository.findByUserId(tpoUserId)).thenReturn(Optional.of(organization));
        when(passwordEncoder.encode(any())).thenReturn("hashed_pass");
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(studentProfileRepository.findByOrganizationIdAndRollNumber(eq(orgId), any())).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> {
            StudentProfile sp = invocation.getArgument(0);
            sp.setId(UUID.randomUUID());
            return sp;
        });

        String csvData = """
                Roll Number,First Name,Last Name,College Email,Mobile Phone,Branch Department,Batch Passing Year,Degree Name,Sem 1 SGPA,Sem 2 SGPA,Sem 3 SGPA,Sem 4 SGPA,Sem 5 SGPA,Sem 6 SGPA,Sem 7 SGPA,Sem 8 SGPA
                2024-CSE-001,Rahul,Sharma,rahul@mgmcet.ac.in,9876543210,Computer Science & Engineering,2024,B.Tech,8.0,8.2,8.4,8.6,8.5,8.7,8.8,9.0
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csvData.getBytes(StandardCharsets.UTF_8)
        );

        BulkUploadResultDto result = service.bulkUploadStudents(tpoUserId, file);

        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getImportedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getImportedStudents().size());

        StudentProfileDto imported = result.getImportedStudents().get(0);
        assertEquals("2024-CSE-001", imported.getRollNumber());
        assertEquals("Rahul", imported.getFirstName());
        assertEquals("Computer Science & Engineering", imported.getBranch());
        assertEquals(8.53, imported.getAggregateCgpa());
        assertEquals("Tier 1", imported.getTier());
    }

    @Test
    void testHodBranchScopingRestriction() {
        // HOD of IT branch trying to onboard a Mechanical student
        CollegeTeamMember hod = CollegeTeamMember.builder()
                .organizationId(orgId)
                .userId(hodUserId)
                .roleName("HOD IT")
                .assignedBranch("Information Technology")
                .build();

        when(organizationRepository.findByUserId(hodUserId)).thenReturn(Optional.empty());
        when(teamMemberRepository.findByUserId(hodUserId)).thenReturn(Optional.of(hod));

        String csvData = """
                Roll Number,First Name,Last Name,College Email,Mobile Phone,Branch Department,Batch Passing Year,Degree Name
                2024-MECH-001,Amit,Patil,amit@mgmcet.ac.in,9876543212,Mechanical Engineering,2024,B.Tech
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csvData.getBytes(StandardCharsets.UTF_8)
        );

        BulkUploadResultDto result = service.bulkUploadStudents(hodUserId, file);
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrors().get(0).contains("You do not have permission to onboard students to branch 'Mechanical Engineering'"));
    }
}
