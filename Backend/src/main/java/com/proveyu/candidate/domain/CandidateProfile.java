package com.proveyu.candidate.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private UUID userId;

    @Column(name = "experience_track", nullable = false, length = 20)
    private String experienceTrack = "FRESHER"; // FRESHER or EXPERIENCED

    @Column(name = "years_of_experience")
    private int yearsOfExperience = 0;

    @Column(name = "uan_number", length = 50)
    private String uanNumber;

    @Column(name = "verified_backend_experience_months")
    private int verifiedBackendExperienceMonths = 0; // Calculated in backend, hidden from candidate

    @Column(name = "college_name", length = 150)
    private String collegeName;

    @Column(name = "degree_branch", length = 100)
    private String degreeBranch;

    @Column(name = "passout_year")
    private Integer passoutYear;

    @Column(name = "skills_list", length = 255)
    private String skillsList;

    @Column(name = "resume_url", length = 255)
    private String resumeUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CandidateProfile() {}

    public CandidateProfile(UUID id, UUID userId, String experienceTrack, int yearsOfExperience, String uanNumber, int verifiedBackendExperienceMonths, String collegeName, String degreeBranch, Integer passoutYear, String skillsList, String resumeUrl, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.experienceTrack = experienceTrack;
        this.yearsOfExperience = yearsOfExperience;
        this.uanNumber = uanNumber;
        this.verifiedBackendExperienceMonths = verifiedBackendExperienceMonths;
        this.collegeName = collegeName;
        this.degreeBranch = degreeBranch;
        this.passoutYear = passoutYear;
        this.skillsList = skillsList;
        this.resumeUrl = resumeUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getExperienceTrack() { return experienceTrack; }
    public void setExperienceTrack(String experienceTrack) { this.experienceTrack = experienceTrack; }

    public int getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(int yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

    public String getUanNumber() { return uanNumber; }
    public void setUanNumber(String uanNumber) { this.uanNumber = uanNumber; }

    public int getVerifiedBackendExperienceMonths() { return verifiedBackendExperienceMonths; }
    public void setVerifiedBackendExperienceMonths(int verifiedBackendExperienceMonths) { this.verifiedBackendExperienceMonths = verifiedBackendExperienceMonths; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static CandidateProfileBuilder builder() { return new CandidateProfileBuilder(); }

    public static class CandidateProfileBuilder {
        private UUID id;
        private UUID userId;
        private String experienceTrack = "FRESHER";
        private int yearsOfExperience;
        private String uanNumber;
        private int verifiedBackendExperienceMonths;
        private String collegeName;
        private String degreeBranch;
        private Integer passoutYear;
        private String skillsList;
        private String resumeUrl;
        private Instant createdAt;
        private Instant updatedAt;

        public CandidateProfileBuilder id(UUID id) { this.id = id; return this; }
        public CandidateProfileBuilder userId(UUID userId) { this.userId = userId; return this; }
        public CandidateProfileBuilder experienceTrack(String experienceTrack) { this.experienceTrack = experienceTrack; return this; }
        public CandidateProfileBuilder yearsOfExperience(int yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; return this; }
        public CandidateProfileBuilder uanNumber(String uanNumber) { this.uanNumber = uanNumber; return this; }
        public CandidateProfileBuilder verifiedBackendExperienceMonths(int verifiedBackendExperienceMonths) { this.verifiedBackendExperienceMonths = verifiedBackendExperienceMonths; return this; }
        public CandidateProfileBuilder collegeName(String collegeName) { this.collegeName = collegeName; return this; }
        public CandidateProfileBuilder degreeBranch(String degreeBranch) { this.degreeBranch = degreeBranch; return this; }
        public CandidateProfileBuilder passoutYear(Integer passoutYear) { this.passoutYear = passoutYear; return this; }
        public CandidateProfileBuilder skillsList(String skillsList) { this.skillsList = skillsList; return this; }
        public CandidateProfileBuilder resumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; return this; }
        public CandidateProfileBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public CandidateProfileBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public CandidateProfile build() {
            return new CandidateProfile(id, userId, experienceTrack, yearsOfExperience, uanNumber, verifiedBackendExperienceMonths, collegeName, degreeBranch, passoutYear, skillsList, resumeUrl, createdAt, updatedAt);
        }
    }
}
