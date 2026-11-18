package com.proveyu.support.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Help & support requests from the college and student portals, answered by the ProveYu team. */
public final class SupportModel {
    private SupportModel() {}

    public enum Category { ACCOUNT, PLACEMENT, MOCK_TEST, AI_INTERVIEW, PAYMENT, BUG, OTHER }
    public enum Status { OPEN, ANSWERED, CLOSED }

    public record NewTicket(@NotNull Category category, @NotBlank @Size(max = 150) String subject, @NotBlank @Size(min = 10, max = 4000) String message) {}
    public record Reply(@NotBlank @Size(max = 4000) String reply, boolean close, boolean sendEmail) {}
    public record StatusChange(@NotNull Status status) {}

    public record Ticket(UUID id, String userName, String userEmail, String userRole, String portal, UUID collegeId, String collegeName,
                         Category category, String subject, String message, Status status, String reply, String repliedBy, Instant repliedAt,
                         Instant createdAt, Instant updatedAt) {}
    public record TicketPage(List<Ticket> rows, long total, int page, int size) {}
}
