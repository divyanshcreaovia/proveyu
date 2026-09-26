package com.proveyu.recruiter.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", columnDefinition = "CHAR(36)")
    private UUID companyId;

    @Column(name = "designation")
    private String designation;

    @Column(name = "work_email")
    private String workEmail;

    @Column(name = "work_email_verified_at")
    private Instant workEmailVerifiedAt;

    @Column(name = "seats_purchased")
    private Integer seatsPurchased = 0;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "city")
    private String city;

    @Column(name = "org_type")
    private String orgType;

    @Column(name = "website")
    private String website;

    @Column(name = "headline")
    private String headline;

    @Column(name = "about", columnDefinition = "TEXT")
    private String about;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "linkedin")
    private String linkedin;

    @Column(name = "hiring_volume")
    private String hiringVolume;

    @Column(name = "candidate_level")
    private String candidateLevel;

    @Column(name = "primary_track")
    private String primaryTrack;

    @Column(name = "preferred_cities")
    private String preferredCities;

    @Column(name = "custom_notes", columnDefinition = "TEXT")
    private String customNotes;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public RecruiterProfile() {}

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public void setWorkEmail(String workEmail) {
        this.workEmail = workEmail;
    }

    public Instant getWorkEmailVerifiedAt() {
        return workEmailVerifiedAt;
    }

    public void setWorkEmailVerifiedAt(Instant workEmailVerifiedAt) {
        this.workEmailVerifiedAt = workEmailVerifiedAt;
    }

    public Integer getSeatsPurchased() {
        return seatsPurchased;
    }

    public void setSeatsPurchased(Integer seatsPurchased) {
        this.seatsPurchased = seatsPurchased;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getOrgType() { return orgType; }
    public void setOrgType(String orgType) { this.orgType = orgType; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getLinkedin() { return linkedin; }
    public void setLinkedin(String linkedin) { this.linkedin = linkedin; }

    public String getHiringVolume() { return hiringVolume; }
    public void setHiringVolume(String hiringVolume) { this.hiringVolume = hiringVolume; }

    public String getCandidateLevel() { return candidateLevel; }
    public void setCandidateLevel(String candidateLevel) { this.candidateLevel = candidateLevel; }

    public String getPrimaryTrack() { return primaryTrack; }
    public void setPrimaryTrack(String primaryTrack) { this.primaryTrack = primaryTrack; }

    public String getPreferredCities() { return preferredCities; }
    public void setPreferredCities(String preferredCities) { this.preferredCities = preferredCities; }

    public String getCustomNotes() { return customNotes; }
    public void setCustomNotes(String customNotes) { this.customNotes = customNotes; }
}
