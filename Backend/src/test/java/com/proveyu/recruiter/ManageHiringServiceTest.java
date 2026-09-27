package com.proveyu.recruiter;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.candidate.domain.CandidateScore;
import com.proveyu.candidate.infrastructure.CandidateScoreRepository;
import com.proveyu.recruiter.application.ManageHiringService;
import com.proveyu.recruiter.domain.Job;
import com.proveyu.recruiter.domain.JobInvitation;
import com.proveyu.recruiter.domain.JobInvitationStatus;
import com.proveyu.recruiter.domain.JobStatus;
import com.proveyu.recruiter.infrastructure.JobInvitationRepository;
import com.proveyu.recruiter.infrastructure.JobRepository;
import com.proveyu.recruiter.web.dto.JobDto.*;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManageHiringServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobInvitationRepository jobInvitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CandidateScoreRepository candidateScoreRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ManageHiringService manageHiringService;

    private UUID recruiterId;
    private UUID candidateId;
    private UUID jobId;
    private User recruiter;
    private User candidate;
    private Job job;

    @BeforeEach
    void setUp() {
        recruiterId = UUID.randomUUID();
        candidateId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        recruiter = User.builder()
                .id(recruiterId)
                .email("recruiter@company.com")
                .fullName("John Recruiter")
                .role(Role.RECRUITER)
                .build();

        candidate = User.builder()
                .id(candidateId)
                .email("candidate@example.com")
                .fullName("Alice Candidate")
                .role(Role.CANDIDATE)
                .build();

        job = Job.builder()
                .id(jobId)
                .postedBy(recruiterId)
                .title("Associate QA Automation Engineer")
                .description("Automate tests using Selenium & Java")
                .roleTrack("QA & Automation Testing")
                .requiredSkills("Java, Selenium, JUnit")
                .experienceLevel("Fresher / 0-1 Yrs")
                .minSalaryLpa(8.0)
                .maxSalaryLpa(10.0)
                .salaryPackage("₹8 - 10 LPA")
                .location("Bengaluru / Remote")
                .workMode("HYBRID")
                .minScoreThreshold(85.0)
                .vacancies(2)
                .targetClient("ProveYu Labs (In-House)")
                .lastDate(LocalDate.now().plusMonths(1))
                .status(JobStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Create Job: Successfully posts a new job requirement")
    void testCreateJob_Success() {
        CreateJobRequest request = CreateJobRequest.builder()
                .title("Associate QA Automation Engineer")
                .description("Automate tests using Selenium & Java")
                .roleTrack("QA & Automation Testing")
                .requiredSkills("Java, Selenium, JUnit")
                .experienceLevel("Fresher / 0-1 Yrs")
                .minSalaryLpa(8.0)
                .maxSalaryLpa(10.0)
                .salaryPackage("₹8 - 10 LPA")
                .location("Bengaluru / Remote")
                .workMode("HYBRID")
                .minScoreThreshold(85.0)
                .vacancies(2)
                .targetClient("ProveYu Labs (In-House)")
                .lastDate(LocalDate.now().plusMonths(1))
                .build();

        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(recruiter));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> {
            Job j = i.getArgument(0);
            j.setId(jobId);
            return j;
        });

        JobResponse response = manageHiringService.createJob(recruiterId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(jobId);
        assertThat(response.getTitle()).isEqualTo("Associate QA Automation Engineer");
        assertThat(response.getStatus()).isEqualTo(JobStatus.ACTIVE);
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    @DisplayName("Send Job Invitation: Dispatches invitation linked to candidate score")
    void testSendJobInvitation_Success() {
        UUID scoreId = UUID.randomUUID();
        CandidateScore score = CandidateScore.builder()
                .id(scoreId)
                .candidateId(candidateId)
                .score(92.0)
                .maxScore(100.0)
                .build();

        SendJobInvitationRequest request = SendJobInvitationRequest.builder()
                .candidateId(candidateId)
                .candidateScoreId(scoreId)
                .scoreSnapshot(92.0)
                .message("We would love to interview you for our QA role.")
                .notes("High score in QA automation assessment.")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(jobInvitationRepository.findByJobIdAndCandidateId(jobId, candidateId)).thenReturn(Optional.empty());
        when(jobInvitationRepository.save(any(JobInvitation.class))).thenAnswer(i -> {
            JobInvitation inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            inv.setCreatedAt(Instant.now());
            return inv;
        });

        JobInvitationResponse response = manageHiringService.sendJobInvitation(jobId, recruiterId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(JobInvitationStatus.INVITED);
        assertThat(response.getCandidateScoreId()).isEqualTo(scoreId);
        assertThat(response.getScoreSnapshot()).isEqualTo(92.0);
        verify(emailService, times(1)).sendInterviewInviteEmail(
                eq(candidate.getEmail()), any(), any(), eq(job.getTitle()), eq(job.getSalaryPackage()));
    }

    @Test
    @DisplayName("Send Job Invitation: Throws 409 Conflict if already invited")
    void testSendJobInvitation_DuplicateConflict() {
        SendJobInvitationRequest request = SendJobInvitationRequest.builder()
                .candidateId(candidateId)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(jobInvitationRepository.findByJobIdAndCandidateId(jobId, candidateId))
                .thenReturn(Optional.of(JobInvitation.builder().build()));

        assertThatThrownBy(() -> manageHiringService.sendJobInvitation(jobId, recruiterId, request))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DUPLICATE_INVITATION")
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(jobInvitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update Invitation Status: Transitions through pipeline stages")
    void testUpdateInvitationStatus_Lifecycle() {
        UUID invitationId = UUID.randomUUID();
        JobInvitation invitation = JobInvitation.builder()
                .id(invitationId)
                .recruiterId(recruiterId)
                .candidateId(candidateId)
                .jobId(jobId)
                .status(JobInvitationStatus.INVITED)
                .build();

        when(jobInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(jobInvitationRepository.save(any(JobInvitation.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Move to ACCEPT
        UpdateJobInvitationStatusRequest reqAccept = UpdateJobInvitationStatusRequest.builder()
                .status(JobInvitationStatus.ACCEPT)
                .notes("Candidate confirmed interest")
                .build();
        JobInvitationResponse res1 = manageHiringService.updateInvitationStatus(invitationId, recruiterId, reqAccept);
        assertThat(res1.getStatus()).isEqualTo(JobInvitationStatus.ACCEPT);

        // 2. Move to INTERVIEWING
        UpdateJobInvitationStatusRequest reqInterview = UpdateJobInvitationStatusRequest.builder()
                .status(JobInvitationStatus.INTERVIEWING)
                .notes("Round 1 scheduled")
                .build();
        JobInvitationResponse res2 = manageHiringService.updateInvitationStatus(invitationId, recruiterId, reqInterview);
        assertThat(res2.getStatus()).isEqualTo(JobInvitationStatus.INTERVIEWING);

        // 3. Move to OFFERED
        UpdateJobInvitationStatusRequest reqOffered = UpdateJobInvitationStatusRequest.builder()
                .status(JobInvitationStatus.OFFERED)
                .notes("Offer letter rolled out: 9.5 LPA")
                .build();
        JobInvitationResponse res3 = manageHiringService.updateInvitationStatus(invitationId, recruiterId, reqOffered);
        assertThat(res3.getStatus()).isEqualTo(JobInvitationStatus.OFFERED);

        // 4. Move to HIERED
        UpdateJobInvitationStatusRequest reqHired = UpdateJobInvitationStatusRequest.builder()
                .status(JobInvitationStatus.HIERED)
                .notes("Candidate joined")
                .build();
        JobInvitationResponse res4 = manageHiringService.updateInvitationStatus(invitationId, recruiterId, reqHired);
        assertThat(res4.getStatus()).isEqualTo(JobInvitationStatus.HIERED);
    }
}
