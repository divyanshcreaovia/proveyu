package com.proveyu.offcampus.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Off-campus jobs posted by the ProveYu team; students apply on the company's site and track their status here. */
public final class OffCampusModel {
    private OffCampusModel() {}

    public enum JobType { FULL_TIME, INTERNSHIP, INTERNSHIP_PPO }
    public enum WorkMode { ONSITE, REMOTE, HYBRID }
    public enum JobStatus { DRAFT, PUBLISHED, CLOSED }
    /** CLICKED is set by ProveYu when the apply link is opened; the student sets the others. */
    public enum AppStatus { CLICKED, APPLIED, INTERVIEWING, OFFER, REJECTED, NOT_INTERESTED }
    /** A college's rule for its students. */
    public enum Policy { ALLOW, BLOCK_PLACED, DISABLED }
    /** Where a job came from: posted by the team, or imported from a job feed. */
    public enum Source { MANUAL, CAREERJET }

    /**
     * The field a job belongs to. {@code search} is what the import searches for; {@code words} classify a title (first match wins,
     * so the more specific fields come first).
     */
    public enum JobField {
        DATA("Data analytics & science", "data analyst", "data analyst", "data scien", "data engineer", "analytics", "machine learning", "power bi",
                "business intelligence", "ml engineer", "ai engineer"),
        DESIGN("UI/UX & design", "ui ux designer", "ui/ux", "ux ", "ui designer", "graphic design", "product design", "designer"),
        CYBER("Networking & cybersecurity", "network engineer", "cyber", "security analyst", "network engineer", "soc analyst", "networking"),
        TESTING("Software testing & QA", "software tester", "qa ", "quality assurance", "tester", "testing", "automation test"),
        SOFTWARE("Software development", "software developer", "software", "developer", "programmer", "full stack", "fullstack", "frontend",
                "front end", "backend", "back end", "java", "python", ".net", "web develop", "android", " ios ", "devops", "cloud", "sde"),
        MECHANICAL("Mechanical engineering", "mechanical engineer", "mechanical", "production engineer", "manufacturing", "maintenance engineer",
                "automobile", "design engineer", " cad ", "autocad"),
        ELECTRICAL("Electrical & electronics", "electrical engineer", "electrical", "electronics", "embedded", "vlsi", "instrumentation", " ece "),
        CIVIL("Civil & construction", "civil engineer", "civil", "site engineer", "construction", "structural", "quantity surveyor"),
        BUSINESS("Business analyst & consulting", "business analyst", "business analyst", "consultant", "operations", "project coordinator"),
        FINANCE("Finance & accounting", "accountant", "account", "finance", "audit", "tax", "banking", "ca article"),
        MARKETING("Marketing & sales", "marketing executive", "marketing", "sales", "business development", "seo", "content", "social media"),
        HR("HR & recruitment", "hr executive", "human resource", "hr ", "recruit", "talent acquisition"),
        OTHER("Other", null);

        public final String label;
        public final String search;
        private final String[] words;

        JobField(String label, String search, String... words) {
            this.label = label;
            this.search = search;
            this.words = words;
        }

        /** The field a job title belongs to, or OTHER. */
        public static JobField classify(String title) {
            if (title == null) return OTHER;
            String t = " " + title.toLowerCase(java.util.Locale.ROOT) + " ";
            for (JobField f : values()) for (String w : f.words) if (t.contains(w)) return f;
            return OTHER;
        }
    }
    public enum NocStatus { PENDING, APPROVED, REJECTED }

    // ------------------------------------------------------------------ admin

    public record JobRequest(
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Size(max = 150) String roleTitle,
            @NotNull JobType jobType,
            @NotNull WorkMode workMode,
            JobField field,
            @Size(max = 150) String location,
            @DecimalMin("0") @DecimalMax("500") BigDecimal ctcMinLpa,
            @DecimalMin("0") @DecimalMax("500") BigDecimal ctcMaxLpa,
            @Min(0) @Max(10_000_000) Integer stipendPerMonth,
            @Size(max = 8000) String description,
            @Size(max = 30) List<@Size(max = 40) String> skills,
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^https?://[^\\s]+$", message = "Use a full link starting with https://") String applyUrl,
            Instant applyDeadline,
            @Size(max = 10) List<@Min(2000) @Max(2100) Integer> batchYears,
            @Size(max = 300) String degreeKeywords,
            @Size(max = 300) String branchKeywords,
            @DecimalMin("0") @DecimalMax("10") BigDecimal minCgpa,
            @Min(0) @Max(50) Integer maxBacklogs,
            @Size(max = 500) List<UUID> collegeIds) {}

    /** sendNotification = false publishes without the bell notification (imported jobs are published in bulk). */
    public record StatusChange(@NotNull JobStatus status, Boolean sendNotification) {}

    public record BulkStatus(@NotNull @Size(min = 1, max = 200) List<UUID> ids, @NotNull JobStatus status) {}

    /** The last nightly (or manual) import. */
    public record ImportRun(boolean configured, Instant startedAt, Instant finishedAt, int fetched, int added, int skipped, int expired, String error) {}

    public record Job(UUID id, String companyName, String roleTitle, JobType jobType, WorkMode workMode, JobField field, String location, BigDecimal ctcMinLpa,
                      BigDecimal ctcMaxLpa, Integer stipendPerMonth, String description, List<String> skills, String applyUrl, Instant applyDeadline,
                      List<Integer> batchYears, String degreeKeywords, String branchKeywords, BigDecimal minCgpa, Integer maxBacklogs,
                      List<UUID> collegeIds, JobStatus status, Instant publishedAt, Instant createdAt, Instant updatedAt, Source source,
                      Instant sourcePostedAt, Counts counts) {}

    /** How students responded to a job (from their own updates). */
    public record Counts(long saved, long clicked, long applied, long interviewing, long offers) {}

    public record JobPage(List<Job> rows, long total, int page, int size) {}

    public record Applicant(UUID id, String studentName, String rollNumber, String email, String collegeName, String branch, Integer batchYear,
                            AppStatus status, boolean saved, BigDecimal offerCtcLpa, String note, Instant clickedAt, Instant appliedAt, Instant updatedAt) {}

    // ------------------------------------------------------------------ student

    /** A job as one student sees it. applyUrl is only sent when the student may apply. */
    public record StudentJob(UUID id, String companyName, String roleTitle, JobType jobType, WorkMode workMode, JobField field, String location, BigDecimal ctcMinLpa,
                             BigDecimal ctcMaxLpa, Integer stipendPerMonth, String description, List<String> skills, String applyUrl,
                             Instant applyDeadline, Instant publishedAt, boolean eligible, List<String> reasons, boolean canApply,
                             AppStatus status, boolean saved, BigDecimal offerCtcLpa, String note, Source source, Noc noc) {}

    /** The student's latest NOC request for a job. */
    public record Noc(UUID id, NocStatus status, String studentNote, String decisionNote, boolean hasNoc, boolean nocGenerated, String decidedByName,
                      Instant decidedAt, Instant createdAt) {}

    /** interests empty = the student has not picked any field yet. */
    public record StudentJobs(Policy policy, List<JobField> interests, List<StudentJob> jobs) {}

    public record Interests(@NotNull @Size(max = 13) List<JobField> fields) {}

    public record StudentUpdate(@NotNull AppStatus status, @DecimalMin("0") @DecimalMax("500") BigDecimal offerCtcLpa, @Size(max = 500) String note) {}

    public record ApplyLink(String url) {}

    // ------------------------------------------------------------------ college

    public record CollegeRow(UUID id, UUID jobId, String companyName, String roleTitle, JobType jobType, UUID studentId, String studentName,
                             String rollNumber, String branch, Integer batchYear, AppStatus status, BigDecimal offerCtcLpa, String note,
                             Instant clickedAt, Instant appliedAt, Instant updatedAt) {}

    public record CollegeView(Policy policy, long students, long applied, long interviewing, long offers, long pendingNocs, List<CollegeRow> rows) {}

    public record CollegeNoc(UUID id, UUID applicationId, String studentName, String rollNumber, String branch, Integer batchYear, String companyName,
                             String roleTitle, JobType jobType, BigDecimal offerCtcLpa, String offerFileName, NocStatus status, String studentNote,
                             String decisionNote, boolean hasNoc, boolean nocGenerated, String decidedByName, Instant decidedAt, Instant createdAt,
                             boolean placementRecorded) {}
}
