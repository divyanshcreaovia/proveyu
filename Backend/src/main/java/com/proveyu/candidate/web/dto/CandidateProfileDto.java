package com.proveyu.candidate.web.dto;

import java.time.Instant;
import java.util.UUID;

public class CandidateProfileDto {

    public static class UpsertProfileRequest {
        private String experienceTrack; // "FRESHER" or "EXPERIENCED"
        private int yearsOfExperience;
        private String uanNumber;
        private String collegeName;
        private String degreeBranch;
        private Integer passoutYear;
        private String skillsList;
        private String resumeUrl;

        public UpsertProfileRequest() {}

        public String getExperienceTrack() { return experienceTrack; }
        public void setExperienceTrack(String experienceTrack) { this.experienceTrack = experienceTrack; }

        public int getYearsOfExperience() { return yearsOfExperience; }
        public void setYearsOfExperience(int yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

        public String getUanNumber() { return uanNumber; }
        public void setUanNumber(String uanNumber) { this.uanNumber = uanNumber; }

        public String getCollegeName() { return collegeName; }
        public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

        public String getDegreeBranch() { return degreeBranch; }
        public void setDegreeBranch(String degreeBranch) { this.degreeBranch = degreeBranch; }

        public Integer getPassoutYear() { return passoutYear; }
        public void setPassoutYear(Integer passoutYear) { this.passoutYear = passoutYear; }

        public String getSkillsList() { return skillsList; }
        public void setSkillsList(String skillsList) { this.skillsList = skillsList; }

        public String getResumeUrl() { return resumeUrl; }
        public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
    }

    public static class ProfileResponse {
        private UUID id;
        private UUID userId;
        private String experienceTrack;
        private int yearsOfExperience;
        private String uanNumber;
        private String collegeName;
        private String degreeBranch;
        private Integer passoutYear;
        private String skillsList;
        private String resumeUrl;
        private Instant updatedAt;

        public ProfileResponse() {}

        public ProfileResponse(UUID id, UUID userId, String experienceTrack, int yearsOfExperience, String uanNumber, String collegeName, String degreeBranch, Integer passoutYear, String skillsList, String resumeUrl, Instant updatedAt) {
            this.id = id;
            this.userId = userId;
            this.experienceTrack = experienceTrack;
            this.yearsOfExperience = yearsOfExperience;
            this.uanNumber = uanNumber;
            this.collegeName = collegeName;
            this.degreeBranch = degreeBranch;
            this.passoutYear = passoutYear;
            this.skillsList = skillsList;
            this.resumeUrl = resumeUrl;
            this.updatedAt = updatedAt;
        }

        public UUID getId() { return id; }
        public UUID getUserId() { return userId; }
        public String getExperienceTrack() { return experienceTrack; }
        public int getYearsOfExperience() { return yearsOfExperience; }
        public String getUanNumber() { return uanNumber; }
        public String getCollegeName() { return collegeName; }
        public String getDegreeBranch() { return degreeBranch; }
        public Integer getPassoutYear() { return passoutYear; }
        public String getSkillsList() { return skillsList; }
        public String getResumeUrl() { return resumeUrl; }
        public Instant getUpdatedAt() { return updatedAt; }
    }
}
