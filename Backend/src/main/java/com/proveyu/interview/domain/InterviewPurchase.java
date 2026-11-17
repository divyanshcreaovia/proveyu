package com.proveyu.interview.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** AI interviews a student bought by UPI QR. They are added to the student's paid balance once an admin approves the payment. */
@Entity
@Table(name = "interview_purchases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewPurchase {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", columnDefinition = "CHAR(36)")
    private UUID collegeId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    private int quantity;
    @Column(name = "amount_paise")
    private int amountPaise;
    @Enumerated(EnumType.STRING)
    private InterviewModel.PurchaseStatus status;
    /** Short code shown to the student and put in the UPI payment note, e.g. PYAB23CD45. */
    private String reference;
    /** 12-digit UPI reference number the student entered. */
    private String utr;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reviewed_by", columnDefinition = "CHAR(36)")
    private UUID reviewedBy;
    @Column(name = "review_note")
    private String reviewNote;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
