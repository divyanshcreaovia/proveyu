package com.proveyu.admin.domain;

import com.proveyu.college.application.CollegeAdminService.CollegeSummary;
import com.proveyu.student.interview.application.AiUsage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requests and views of the platform admin portal. */
public final class AdminModel {
    private AdminModel() {}

    // ---------------------------------------------------------------- dashboard
    public record Dashboard(Map<String, Long> colleges, Map<String, Long> users, long blockedUsers, long students, long placedStudents,
                            long drives, long liveDrives, long offers, long acceptedOffers, long mockAttempts, long flaggedAttempts,
                            long autoSubmittedAttempts, long interviews, long completedInterviews, long paymentsWaiting, long revenueRupees,
                            long revenueThisMonthRupees, AiUsage.Snapshot ai, List<CollegeSummary> pendingColleges, List<AuditRow> recentActions) {}

    // ---------------------------------------------------------------- colleges
    public record CollegeDetail(CollegeSummary college, long students, long placedStudents, long drives, long offers, long mockAttempts,
                                long interviews, List<Member> team) {}
    public record Member(UUID userId, String fullName, String email, String role, String status, boolean blocked) {}

    // ---------------------------------------------------------------- users
    /** detail: roll number for a student, college role for staff. */
    public record UserRow(UUID id, String fullName, String email, String phone, String role, boolean active, boolean verified, Instant createdAt,
                          String collegeName, String detail) {}
    public record UserPage(List<UserRow> rows, long total, int page, int size) {}
    public record CreateAdminRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 150) String fullName,
                                     @NotBlank @Size(min = 8, max = 100) String password) {}
    public record AdminRow(UUID id, String fullName, String email, boolean active, Instant createdAt) {}
    public record CreditRequest(@NotBlank @Email String studentEmail, int count) {}

    // ---------------------------------------------------------------- broadcast
    public enum Audience { ALL, STUDENTS, COLLEGE_STAFF, COLLEGE }
    public record BroadcastRequest(@NotNull Audience audience, UUID collegeId, @NotBlank @Size(max = 120) String title,
                                   @NotBlank @Size(max = 1000) String message, boolean sendEmail) {}
    public record BroadcastResult(int recipients, int emails) {}

    // ---------------------------------------------------------------- audit
    public record AuditRow(UUID id, String adminName, String action, String targetType, String targetId, String summary, Instant createdAt) {}
    public record AuditPage(List<AuditRow> rows, long total, int page, int size) {}

    // ---------------------------------------------------------------- public site
    public record SiteBanner(boolean enabled, String text, String link) {}
}
