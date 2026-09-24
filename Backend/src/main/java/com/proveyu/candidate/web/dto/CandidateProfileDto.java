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
        private String fullName;
        private String phone;
        private String location;
        private String headline;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
        private String bio;

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

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getHeadline() { return headline; }
        public void setHeadline(String headline) { this.headline = headline; }

        public String getLinkedinUrl() { return linkedinUrl; }
        public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

        public String getGithubUrl() { return githubUrl; }
        public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

        public String getPortfolioUrl() { return portfolioUrl; }
        public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }

        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
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
        private String fullName;
        private String email;
        private String phone;
        private String location;
        private String headline;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
        private String bio;
        private Instant updatedAt;

        public ProfileResponse() {}

        public ProfileResponse(UUID id, UUID userId, String experienceTrack, int yearsOfExperience, String uanNumber, String collegeName, String degreeBranch, Integer passoutYear, String skillsList, String resumeUrl, String fullName, String email, String phone, String location, String headline, String linkedinUrl, String githubUrl, String portfolioUrl, String bio, Instant updatedAt) {
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
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.location = location;
            this.headline = headline;
            this.linkedinUrl = linkedinUrl;
            this.githubUrl = githubUrl;
            this.portfolioUrl = portfolioUrl;
            this.bio = bio;
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
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getLocation() { return location; }
        public String getHeadline() { return headline; }
        public String getLinkedinUrl() { return linkedinUrl; }
        public String getGithubUrl() { return githubUrl; }
        public String getPortfolioUrl() { return portfolioUrl; }
        public String getBio() { return bio; }
        public Instant getUpdatedAt() { return updatedAt; }
    }
}
