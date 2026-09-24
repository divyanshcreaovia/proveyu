package com.proveyu.booking.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID candidateId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "slot_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID slotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking() {}

    public Booking(UUID id, UUID candidateId, UUID slotId, BookingStatus status, Instant heldAt, Instant expiresAt, Instant confirmedAt, Instant createdAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.slotId = slotId;
        this.status = status;
        this.heldAt = heldAt;
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID candidateId) { this.candidateId = candidateId; }

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public Instant getHeldAt() { return heldAt; }
    public void setHeldAt(Instant heldAt) { this.heldAt = heldAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Booking hold(UUID candidateId, UUID slotId, Duration holdDuration) {
        Instant now = Instant.now();
        return builder()
                .candidateId(candidateId)
                .slotId(slotId)
                .status(BookingStatus.HELD)
                .heldAt(now)
                .expiresAt(now.plus(holdDuration))
                .build();
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public void expire() {
        this.status = BookingStatus.EXPIRED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public static BookingBuilder builder() { return new BookingBuilder(); }

    public static class BookingBuilder {
        private UUID id;
        private UUID candidateId;
        private UUID slotId;
        private BookingStatus status;
        private Instant heldAt;
        private Instant expiresAt;
        private Instant confirmedAt;
        private Instant createdAt;

        public BookingBuilder id(UUID id) { this.id = id; return this; }
        public BookingBuilder candidateId(UUID candidateId) { this.candidateId = candidateId; return this; }
        public BookingBuilder slotId(UUID slotId) { this.slotId = slotId; return this; }
        public BookingBuilder status(BookingStatus status) { this.status = status; return this; }
        public BookingBuilder heldAt(Instant heldAt) { this.heldAt = heldAt; return this; }
        public BookingBuilder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public BookingBuilder confirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; return this; }
        public BookingBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Booking build() {
            return new Booking(id, candidateId, slotId, status, heldAt, expiresAt, confirmedAt, createdAt);
        }
    }
}
