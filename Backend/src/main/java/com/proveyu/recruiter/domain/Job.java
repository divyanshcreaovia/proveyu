package com.proveyu.recruiter.domain;

import com.proveyu.auth.domain.User;
import com.proveyu.assessment.domain.Domain;
import com.proveyu.verification.domain.Company;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_posted_by", columnList = "posted_by"),
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_role_track", columnList = "role_track"),
        @Index(name = "idx_jobs_domain", columnList = "domain_id"),
        @Index(name = "idx_jobs_last_date", columnList = "last_date")
})
public class Job {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "posted_by", nullable = false, columnDefinition = "CHAR(36)")
    private UUID postedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by", insertable = false, updatable = false)
    private User poster;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", columnDefinition = "CHAR(36)")
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "domain_id", columnDefinition = "CHAR(36)")
    private UUID domainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", insertable = false, updatable = false)
    private Domain domain;


    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "role_track", length = 100)
    private String roleTrack;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @Column(name = "min_experience_years")
    private Integer minExperienceYears = 0;

    @Column(name = "max_experience_years")
    private Integer maxExperienceYears;

    @Column(name = "min_salary_lpa")
    private Double minSalaryLpa;

    @Column(name = "max_salary_lpa")
    private Double maxSalaryLpa;

    @Column(name = "salary_package", length = 100)
    private String salaryPackage;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "work_mode", length = 50)
    private String workMode = "ON_SITE";

    @Column(name = "min_score_threshold")
    private Double minScoreThreshold = 0.0;

    @Column(name = "vacancies")
    private Integer vacancies = 1;

    @Column(name = "target_client", length = 255)
    private String targetClient;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JobStatus status = JobStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Job() {}

    public Job(UUID id, UUID postedBy, UUID companyId, UUID domainId, String title, String description, String roleTrack,
               String requiredSkills, String experienceLevel, Integer minExperienceYears, Integer maxExperienceYears,
               Double minSalaryLpa, Double maxSalaryLpa, String salaryPackage, String location, String workMode,
               Double minScoreThreshold, Integer vacancies, String targetClient, LocalDate lastDate,
               JobStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.postedBy = postedBy;
        this.companyId = companyId;
        this.domainId = domainId;
        this.title = title;
        this.description = description;
        this.roleTrack = roleTrack;
        this.requiredSkills = requiredSkills;
        this.experienceLevel = experienceLevel;
        this.minExperienceYears = minExperienceYears != null ? minExperienceYears : 0;
        this.maxExperienceYears = maxExperienceYears;
        this.minSalaryLpa = minSalaryLpa;
        this.maxSalaryLpa = maxSalaryLpa;
        this.salaryPackage = salaryPackage;
        this.location = location;
        this.workMode = workMode != null ? workMode : "ON_SITE";
        this.minScoreThreshold = minScoreThreshold != null ? minScoreThreshold : 0.0;
        this.vacancies = vacancies != null ? vacancies : 1;
        this.targetClient = targetClient;
        this.lastDate = lastDate;
        this.status = status != null ? status : JobStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPostedBy() { return postedBy; }
    public void setPostedBy(UUID postedBy) { this.postedBy = postedBy; }

    public User getPoster() { return poster; }

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public Company getCompany() { return company; }

    public UUID getDomainId() { return domainId; }
    public void setDomainId(UUID domainId) { this.domainId = domainId; }

    public Domain getDomain() { return domain; }
    public void setDomain(Domain domain) { this.domain = domain; }


    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRoleTrack() { return roleTrack; }
    public void setRoleTrack(String roleTrack) { this.roleTrack = roleTrack; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public Integer getMinExperienceYears() { return minExperienceYears; }
    public void setMinExperienceYears(Integer minExperienceYears) { this.minExperienceYears = minExperienceYears; }

    public Integer getMaxExperienceYears() { return maxExperienceYears; }
    public void setMaxExperienceYears(Integer maxExperienceYears) { this.maxExperienceYears = maxExperienceYears; }

    public Double getMinSalaryLpa() { return minSalaryLpa; }
    public void setMinSalaryLpa(Double minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; }

    public Double getMaxSalaryLpa() { return maxSalaryLpa; }
    public void setMaxSalaryLpa(Double maxSalaryLpa) { this.maxSalaryLpa = maxSalaryLpa; }

    public String getSalaryPackage() { return salaryPackage; }
    public void setSalaryPackage(String salaryPackage) { this.salaryPackage = salaryPackage; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }

    public Double getMinScoreThreshold() { return minScoreThreshold; }
    public void setMinScoreThreshold(Double minScoreThreshold) { this.minScoreThreshold = minScoreThreshold; }

    public Integer getVacancies() { return vacancies; }
    public void setVacancies(Integer vacancies) { this.vacancies = vacancies; }

    public String getTargetClient() { return targetClient; }
    public void setTargetClient(String targetClient) { this.targetClient = targetClient; }

    public LocalDate getLastDate() { return lastDate; }
    public void setLastDate(LocalDate lastDate) { this.lastDate = lastDate; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static JobBuilder builder() { return new JobBuilder(); }

    public static class JobBuilder {
        private UUID id;
        private UUID postedBy;
        private UUID companyId;
        private UUID domainId;
        private String title;
        private String description;
        private String roleTrack;
        private String requiredSkills;
        private String experienceLevel;
        private Integer minExperienceYears = 0;
        private Integer maxExperienceYears;
        private Double minSalaryLpa;
        private Double maxSalaryLpa;
        private String salaryPackage;
        private String location;
        private String workMode = "ON_SITE";
        private Double minScoreThreshold = 0.0;
        private Integer vacancies = 1;
        private String targetClient;
        private LocalDate lastDate;
        private JobStatus status = JobStatus.ACTIVE;
        private Instant createdAt;
        private Instant updatedAt;

        public JobBuilder id(UUID id) { this.id = id; return this; }
        public JobBuilder postedBy(UUID postedBy) { this.postedBy = postedBy; return this; }
        public JobBuilder companyId(UUID companyId) { this.companyId = companyId; return this; }
        public JobBuilder domainId(UUID domainId) { this.domainId = domainId; return this; }
        public JobBuilder title(String title) { this.title = title; return this; }
        public JobBuilder description(String description) { this.description = description; return this; }
        public JobBuilder roleTrack(String roleTrack) { this.roleTrack = roleTrack; return this; }
        public JobBuilder requiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; return this; }
        public JobBuilder experienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; return this; }
        public JobBuilder minExperienceYears(Integer minExperienceYears) { this.minExperienceYears = minExperienceYears; return this; }
        public JobBuilder maxExperienceYears(Integer maxExperienceYears) { this.maxExperienceYears = maxExperienceYears; return this; }
        public JobBuilder minSalaryLpa(Double minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; return this; }
        public JobBuilder maxSalaryLpa(Double maxSalaryLpa) { this.maxSalaryLpa = maxSalaryLpa; return this; }
        public JobBuilder salaryPackage(String salaryPackage) { this.salaryPackage = salaryPackage; return this; }
        public JobBuilder location(String location) { this.location = location; return this; }
        public JobBuilder workMode(String workMode) { this.workMode = workMode; return this; }
        public JobBuilder minScoreThreshold(Double minScoreThreshold) { this.minScoreThreshold = minScoreThreshold; return this; }
        public JobBuilder vacancies(Integer vacancies) { this.vacancies = vacancies; return this; }
        public JobBuilder targetClient(String targetClient) { this.targetClient = targetClient; return this; }
        public JobBuilder lastDate(LocalDate lastDate) { this.lastDate = lastDate; return this; }
        public JobBuilder status(JobStatus status) { this.status = status; return this; }
        public JobBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public JobBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Job build() {
            return new Job(id, postedBy, companyId, domainId, title, description, roleTrack, requiredSkills,
                    experienceLevel, minExperienceYears, maxExperienceYears, minSalaryLpa, maxSalaryLpa,
                    salaryPackage, location, workMode, minScoreThreshold, vacancies, targetClient,
                    lastDate, status, createdAt, updatedAt);
        }
    }
}
