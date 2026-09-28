package com.proveyu.recruiter.web.dto;

import com.proveyu.recruiter.domain.Job;
import com.proveyu.recruiter.domain.JobInvitation;
import com.proveyu.recruiter.domain.JobInvitationStatus;
import com.proveyu.recruiter.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class JobDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateJobRequest {
        @NotBlank(message = "Job title is required")
        private String title;

        private String description;
        private UUID domainId;
        private String domainName;
        private String roleTrack;
        private String requiredSkills;
        private String experienceLevel;
        private Integer minExperienceYears;
        private Integer maxExperienceYears;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String salaryPackage;
        private String location;
        private String workMode;
        private Double minScoreThreshold;
        private Integer vacancies;
        private String targetClient;
        private LocalDate lastDate;
        private UUID companyId;
        private String interviewRounds;
        private Integer totalRounds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateJobRequest {
        private String title;
        private String description;
        private UUID domainId;
        private String domainName;
        private String roleTrack;
        private String requiredSkills;
        private String experienceLevel;
        private Integer minExperienceYears;
        private Integer maxExperienceYears;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String salaryPackage;
        private String location;
        private String workMode;
        private Double minScoreThreshold;
        private Integer vacancies;
        private String targetClient;
        private LocalDate lastDate;
        private JobStatus status;
        private String interviewRounds;
        private Integer totalRounds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobResponse {
        private UUID id;
        private UUID postedBy;
        private String posterName;
        private UUID companyId;
        private String companyName;
        private String title;
        private String description;
        private UUID domainId;
        private String domainName;
        private String roleTrack;
        private String requiredSkills;
        private String experienceLevel;
        private Integer minExperienceYears;
        private Integer maxExperienceYears;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String salaryPackage;
        private String location;
        private String workMode;
        private Double minScoreThreshold;
        private Integer vacancies;
        private String targetClient;
        private LocalDate lastDate;
        private JobStatus status;
        private String interviewRounds;
        private Integer totalRounds;
        private long totalInvitationsCount;
        private Instant createdAt;
        private Instant updatedAt;

        public static JobResponse fromEntity(Job job, long invitationsCount) {
            return JobResponse.builder()
                    .id(job.getId())
                    .postedBy(job.getPostedBy())
                    .posterName(job.getPoster() != null ? job.getPoster().getFullName() : null)
                    .companyId(job.getCompanyId())
                    .companyName(job.getCompany() != null ? job.getCompany().getLegalName() : null)
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .domainId(job.getDomainId())
                    .domainName(job.getDomain() != null ? job.getDomain().getName() : job.getRoleTrack())
                    .roleTrack(job.getRoleTrack())
                    .requiredSkills(job.getRequiredSkills())
                    .experienceLevel(job.getExperienceLevel())
                    .minExperienceYears(job.getMinExperienceYears())
                    .maxExperienceYears(job.getMaxExperienceYears())
                    .minSalaryLpa(job.getMinSalaryLpa())
                    .maxSalaryLpa(job.getMaxSalaryLpa())
                    .salaryPackage(job.getSalaryPackage())
                    .location(job.getLocation())
                    .workMode(job.getWorkMode())
                    .minScoreThreshold(job.getMinScoreThreshold())
                    .vacancies(job.getVacancies())
                    .targetClient(job.getTargetClient())
                    .lastDate(job.getLastDate())
                    .status(job.getStatus())
                    .interviewRounds(job.getInterviewRounds())
                    .totalRounds(job.getTotalRounds())
                    .totalInvitationsCount(invitationsCount)
                    .createdAt(job.getCreatedAt())
                    .updatedAt(job.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendJobInvitationRequest {
        @NotNull(message = "Candidate ID is required")
        private UUID candidateId;

        private UUID candidateScoreId;
        private Double scoreSnapshot;
        private String interviewRound;
        private String message;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateJobInvitationStatusRequest {
        @NotNull(message = "Status is required")
        private JobInvitationStatus status;

        private String interviewRound;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortlistCandidateRequest {
        @NotNull(message = "Candidate ID is required")
        private UUID candidateId;

        private UUID jobId;
        private Double scoreSnapshot;
        private String message;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobInvitationResponse {
        private UUID id;
        private UUID recruiterId;
        private String recruiterName;
        private String recruiterRole;
        private UUID candidateId;
        private String candidateName;
        private String candidateEmail;
        private String candidatePhone;
        private String candidateLocation;
        private String candidateExperience;
        private String candidateSkills;
        private UUID jobId;
        private String jobTitle;
        private String roleTrack;
        private String companyName;
        private String salaryPackage;
        private String location;
        private String experienceLevel;
        private String workMode;
        private String jobDescription;
        private String requiredSkills;
        private UUID candidateScoreId;
        private Double scoreSnapshot;
        private JobInvitationStatus status;
        private String interviewRound;
        private String interviewRounds;
        private Integer totalRounds;
        private String message;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;

        public static JobInvitationResponse fromEntity(JobInvitation inv) {
            String company = null;
            String salary = null;
            String loc = null;
            String expLevel = null;
            String mode = null;
            String desc = null;
            String skills = null;
            String title = null;
            String track = null;

            String interviewRounds = null;
            Integer totalRounds = null;
            if (inv.getJob() != null) {
                title = inv.getJob().getTitle();
                track = inv.getJob().getRoleTrack();
                company = inv.getJob().getTargetClient();
                salary = inv.getJob().getSalaryPackage();
                loc = inv.getJob().getLocation();
                expLevel = inv.getJob().getExperienceLevel();
                mode = inv.getJob().getWorkMode();
                desc = inv.getJob().getDescription();
                skills = inv.getJob().getRequiredSkills();
                interviewRounds = inv.getJob().getInterviewRounds();
                totalRounds = inv.getJob().getTotalRounds();
            }

            if (company == null || company.strip() == "") {
                company = (inv.getRecruiter() != null && inv.getRecruiter().getFullName() != null)
                        ? inv.getRecruiter().getFullName() + " Hiring"
                        : "ProveYu Partner";
            }
            if (salary == null || salary.strip() == "") {
                salary = "₹18 – ₹24 LPA";
            }
            if (loc == null || loc.strip() == "") {
                loc = "Bengaluru (Hybrid)";
            }
            if (expLevel == null || expLevel.strip() == "") {
                expLevel = "Mid Level";
            }

            return JobInvitationResponse.builder()
                    .id(inv.getId())
                    .recruiterId(inv.getRecruiterId())
                    .recruiterName(inv.getRecruiter() != null ? inv.getRecruiter().getFullName() : "Senior Technical Recruiter")
                    .recruiterRole("Technical Recruiter at " + company)
                    .candidateId(inv.getCandidateId())
                    .candidateName(inv.getCandidate() != null ? inv.getCandidate().getFullName() : null)
                    .candidateEmail(inv.getCandidate() != null ? inv.getCandidate().getEmail() : null)
                    .candidatePhone(inv.getCandidate() != null ? inv.getCandidate().getPhone() : null)
                    .jobId(inv.getJobId())
                    .jobTitle(title != null ? title : "Verified Engineering Role")
                    .roleTrack(track)
                    .companyName(company)
                    .salaryPackage(salary)
                    .location(loc)
                    .experienceLevel(expLevel)
                    .workMode(mode != null ? mode : "ON_SITE")
                    .jobDescription(desc != null ? desc : "Participate in core product engineering and verified skills assessment.")
                    .requiredSkills(skills)
                    .candidateScoreId(inv.getCandidateScoreId())
                    .scoreSnapshot(inv.getScoreSnapshot())
                    .status(inv.getStatus())
                    .interviewRound(inv.getInterviewRound())
                    .interviewRounds(interviewRounds)
                    .totalRounds(totalRounds)
                    .message(inv.getMessage())
                    .notes(inv.getNotes())
                    .createdAt(inv.getCreatedAt())
                    .updatedAt(inv.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateMatchResponse {
        private UUID candidateId;
        private String name;
        private String email;
        private String phone;
        private String location;
        private String collegeExp;
        private UUID domainId;
        private String domainName;
        private Double score;
        private String percentileBadge;
        private String tierBadge;
        private String skillsList;
        private String headline;
        private String verifiedCenter;
        private String passportId;
        private boolean isInvited;
        private boolean isShortlisted;
        private JobInvitationStatus invitationStatus;
        private String currentRound;
        private String interviewRounds;
        private Integer totalRounds;
        private Instant lastUpdated;
    }
}
