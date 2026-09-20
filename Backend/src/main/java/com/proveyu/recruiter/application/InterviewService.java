package com.proveyu.recruiter.application;

import com.proveyu.recruiter.domain.InterviewInvite;
import com.proveyu.recruiter.domain.InterviewInviteStatus;
import com.proveyu.recruiter.domain.PlacementStatus;
import com.proveyu.recruiter.infrastructure.InterviewInviteRepository;
import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewInviteRepository interviewInviteRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public InterviewInvite sendInterviewInvite(UUID recruiterId, UUID candidateId, UUID companyId, UUID examId, String jobTitle, String jobDescription, Double minSalaryLpa, Double maxSalaryLpa, String packageRange) {
        InterviewInvite invite = InterviewInvite.builder()
                .recruiterId(recruiterId)
                .candidateId(candidateId)
                .companyId(companyId)
                .examId(examId)
                .jobTitle(jobTitle)
                .jobDescription(jobDescription)
                .minSalaryLpa(minSalaryLpa)
                .maxSalaryLpa(maxSalaryLpa)
                .packageRange(packageRange)
                .status(InterviewInviteStatus.PENDING)
                .placementStatus(PlacementStatus.IN_PROCESS)
                .build();

        InterviewInvite savedInvite = interviewInviteRepository.save(invite);

        // Asynchronous Email to Candidate
        userRepository.findById(candidateId).ifPresent(candidate -> {
            String recruiterName = userRepository.findById(recruiterId)
                    .map(User::getFullName)
                    .orElse("PROVEYU Recruiter");

            emailService.sendInterviewInviteEmail(
                    candidate.getEmail(),
                    candidate.getFullName(),
                    recruiterName,
                    jobTitle,
                    packageRange
            );
        });

        return savedInvite;
    }

    @Transactional(readOnly = true)
    public List<InterviewInvite> getCandidateInvites(UUID candidateId) {
        return interviewInviteRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public List<InterviewInvite> getRecruiterSentInvites(UUID recruiterId) {
        return interviewInviteRepository.findByRecruiterId(recruiterId);
    }

    @Transactional
    public InterviewInvite respondToInterviewInvite(UUID candidateId, UUID inviteId, boolean accept) {
        InterviewInvite invite = interviewInviteRepository.findById(inviteId)
                .orElseThrow(() -> new DomainException("Interview invitation not found", HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND"));

        if (!invite.getCandidateId().equals(candidateId)) {
            throw new DomainException("Unauthorized to respond to this invitation", HttpStatus.FORBIDDEN, "UNAUTHORIZED_INVITE_RESPONSE");
        }

        invite.setStatus(accept ? InterviewInviteStatus.ACCEPTED : InterviewInviteStatus.REJECTED);
        return interviewInviteRepository.save(invite);
    }

    @Transactional
    public InterviewInvite updatePlacementStatus(UUID inviteId, PlacementStatus placementStatus) {
        InterviewInvite invite = interviewInviteRepository.findById(inviteId)
                .orElseThrow(() -> new DomainException("Interview invitation not found", HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND"));

        invite.setPlacementStatus(placementStatus);
        return interviewInviteRepository.save(invite);
    }
}
