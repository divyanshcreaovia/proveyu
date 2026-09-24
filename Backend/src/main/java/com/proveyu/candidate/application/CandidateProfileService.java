package com.proveyu.candidate.application;

import com.proveyu.assessment.domain.Exam;
import com.proveyu.assessment.infrastructure.ExamRepository;
import com.proveyu.assessment.infrastructure.SkillRepository;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.candidate.domain.CandidateProfile;
import com.proveyu.candidate.domain.CandidateScore;
import com.proveyu.candidate.infrastructure.CandidateProfileRepository;
import com.proveyu.candidate.infrastructure.CandidateScoreRepository;
import com.proveyu.candidate.web.dto.CandidatePassportDto.*;
import com.proveyu.candidate.web.dto.CandidateProfileDto.*;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.proveyu.candidate.infrastructure.UanVerificationService;
import com.proveyu.candidate.infrastructure.UanVerificationService.UanVerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.proveyu.shared.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository profileRepository;
    private final CandidateScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final SkillRepository skillRepository;
    private final UanVerificationService uanVerificationService;
    private final FileStorageService fileStorageService;

    @Transactional
    public ProfileResponse uploadResume(UUID candidateId, MultipartFile file) {
        CandidateProfile profile = profileRepository.findByUserId(candidateId)
                .orElse(CandidateProfile.builder().userId(candidateId).experienceTrack("FRESHER").build());

        String storedFileName = fileStorageService.storeFile(file, candidateId);
        String downloadUrl = "/api/v1/candidates/resume/download/" + storedFileName;

        profile.setResumeUrl(downloadUrl);
        CandidateProfile saved = profileRepository.save(profile);

        return getProfile(candidateId);
    }

    @Transactional(readOnly = true)
    public Resource loadResumeResource(String fileName) {
        return fileStorageService.loadFileAsResource(fileName);
    }

    @Transactional
    public ProfileResponse upsertProfile(UUID candidateId, UpsertProfileRequest request) {
        User user = userRepository.findById(candidateId)
                .orElseThrow(() -> new DomainException("Candidate not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        CandidateProfile profile = profileRepository.findByUserId(candidateId)
                .orElse(CandidateProfile.builder().userId(candidateId).build());

        String track = (request.getExperienceTrack() != null && request.getExperienceTrack().equalsIgnoreCase("EXPERIENCED"))
                ? "EXPERIENCED" : "FRESHER";

        profile.setExperienceTrack(track);
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setUanNumber(request.getUanNumber());
        profile.setCollegeName(request.getCollegeName());
        profile.setDegreeBranch(request.getDegreeBranch());
        profile.setPassoutYear(request.getPassoutYear());
        profile.setSkillsList(request.getSkillsList());
        profile.setResumeUrl(request.getResumeUrl());
        profile.setLocation(request.getLocation());
        profile.setHeadline(request.getHeadline());
        profile.setLinkedinUrl(request.getLinkedinUrl());
        profile.setGithubUrl(request.getGithubUrl());
        profile.setPortfolioUrl(request.getPortfolioUrl());
        profile.setBio(request.getBio());

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        userRepository.save(user);

        // UAN EPFO Backend Experience verification calculation (hidden from candidate response DTO)
        if ("EXPERIENCED".equalsIgnoreCase(track) && request.getUanNumber() != null) {
            UanVerificationResult uanResult = uanVerificationService.verifyUanExperience(
                    request.getUanNumber(), request.getYearsOfExperience());
            profile.setVerifiedBackendExperienceMonths(uanResult.getTotalVerifiedMonths());
        } else if ("EXPERIENCED".equalsIgnoreCase(track) && request.getYearsOfExperience() > 0) {
            profile.setVerifiedBackendExperienceMonths(request.getYearsOfExperience() * 12);
        } else {
            profile.setVerifiedBackendExperienceMonths(0);
        }

        CandidateProfile saved = profileRepository.save(profile);

        return new ProfileResponse(
                saved.getId(),
                saved.getUserId(),
                saved.getExperienceTrack(),
                saved.getYearsOfExperience(),
                saved.getUanNumber(),
                saved.getCollegeName(),
                saved.getDegreeBranch(),
                saved.getPassoutYear(),
                saved.getSkillsList(),
                saved.getResumeUrl(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                saved.getLocation(),
                saved.getHeadline(),
                saved.getLinkedinUrl(),
                saved.getGithubUrl(),
                saved.getPortfolioUrl(),
                saved.getBio(),
                saved.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID candidateId) {
        User user = userRepository.findById(candidateId)
                .orElseThrow(() -> new DomainException("Candidate not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        CandidateProfile profile = profileRepository.findByUserId(candidateId)
                .orElseGet(() -> CandidateProfile.builder().userId(candidateId).experienceTrack("FRESHER").build());

        return new ProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getExperienceTrack(),
                profile.getYearsOfExperience(),
                profile.getUanNumber(),
                profile.getCollegeName(),
                profile.getDegreeBranch(),
                profile.getPassoutYear(),
                profile.getSkillsList(),
                profile.getResumeUrl(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                profile.getLocation(),
                profile.getHeadline(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getBio(),
                profile.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PassportResponse getPassport(UUID candidateId) {
        User user = userRepository.findById(candidateId)
                .orElseThrow(() -> new DomainException("Candidate not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        CandidateProfile profile = profileRepository.findByUserId(candidateId).orElse(null);
        String track = profile != null ? profile.getExperienceTrack() : "FRESHER";

        List<CandidateScore> scores = scoreRepository.findByCandidateId(candidateId);

        // Group by Skill
        Map<UUID, List<CandidateScore>> scoresBySkill = scores.stream()
                .collect(Collectors.groupingBy(CandidateScore::getSkillId));

        List<SkillScoreSummary> skillSummaries = new ArrayList<>();
        for (Map.Entry<UUID, List<CandidateScore>> entry : scoresBySkill.entrySet()) {
            UUID skillId = entry.getKey();
            List<CandidateScore> skillScores = entry.getValue();

            double avgPct = skillScores.stream()
                    .mapToDouble(s -> (s.getScore() / s.getMaxScore()) * 100.0)
                    .average().orElse(0.0);

            String skillName = skillRepository.findById(skillId)
                    .map(s -> s.getName())
                    .orElse("Skill (" + skillId.toString().substring(0, 8) + ")");

            skillSummaries.add(new SkillScoreSummary(skillId, skillName, Math.round(avgPct * 100.0) / 100.0, skillScores.size()));
        }

        // Exam history
        List<ExamScoreSummary> examSummaries = scores.stream().map(s -> {
            String title = examRepository.findById(s.getExamId())
                    .map(Exam::getTitle)
                    .orElse("Exam Assessment");
            double pct = Math.round((s.getScore() / s.getMaxScore()) * 100.0 * 100.0) / 100.0;
            return new ExamScoreSummary(s.getId(), s.getExamId(), title, s.getScore(), s.getMaxScore(), pct, s.isPassed());
        }).collect(Collectors.toList());

        double overallAvg = scores.isEmpty() ? 0.0 : scores.stream()
                .mapToDouble(s -> (s.getScore() / s.getMaxScore()) * 100.0)
                .average().orElse(0.0);

        overallAvg = Math.round(overallAvg * 100.0) / 100.0;
        String status = overallAvg >= 60.0 ? "VERIFIED_PASSED" : (scores.isEmpty() ? "NO_TESTS_TAKEN" : "NEEDS_IMPROVEMENT");

        return new PassportResponse(
                candidateId,
                user.getFullName(),
                user.getEmail(),
                track,
                overallAvg,
                status,
                skillSummaries,
                examSummaries
        );
    }
}
