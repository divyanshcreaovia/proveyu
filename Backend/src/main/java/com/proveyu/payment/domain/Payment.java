package com.proveyu.payment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "booking_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID bookingId;

    @Column(nullable = false, length = 30)
    private String gateway = "RAZORPAY";

    @Column(name = "gateway_order_id", nullable = false, length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "upi_transaction_id", length = 100)
    private String upiTransactionId;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment() {}

    public Payment(UUID id, UUID bookingId, String gateway, String gatewayOrderId, String gatewayPaymentId, String upiTransactionId, int amountCents, String currency, PaymentStatus status, String idempotencyKey, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.bookingId = bookingId;
        this.gateway = gateway != null ? gateway : "RAZORPAY";
        this.gatewayOrderId = gatewayOrderId;
        this.gatewayPaymentId = gatewayPaymentId;
        this.upiTransactionId = upiTransactionId;
        this.amountCents = amountCents;
        this.currency = currency != null ? currency : "INR";
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getUpiTransactionId() { return upiTransactionId; }
    public void setUpiTransactionId(String upiTransactionId) { this.upiTransactionId = upiTransactionId; }

    public int getAmountCents() { return amountCents; }
    public void setAmountCents(int amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static PaymentBuilder builder() { return new PaymentBuilder(); }

    public static class PaymentBuilder {
        private UUID id;
        private UUID bookingId;
        private String gateway = "RAZORPAY";
        private String gatewayOrderId;
        private String gatewayPaymentId;
        private String upiTransactionId;
        private int amountCents;
        private String currency = "INR";
        private PaymentStatus status;
        private String idempotencyKey;
        private Instant createdAt;
        private Instant updatedAt;

        public PaymentBuilder id(UUID id) { this.id = id; return this; }
        public PaymentBuilder bookingId(UUID bookingId) { this.bookingId = bookingId; return this; }
        public PaymentBuilder gateway(String gateway) { this.gateway = gateway; return this; }
        public PaymentBuilder gatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; return this; }
        public PaymentBuilder gatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; return this; }
        public PaymentBuilder upiTransactionId(String upiTransactionId) { this.upiTransactionId = upiTransactionId; return this; }
        public PaymentBuilder amountCents(int amountCents) { this.amountCents = amountCents; return this; }
        public PaymentBuilder currency(String currency) { this.currency = currency; return this; }
        public PaymentBuilder status(PaymentStatus status) { this.status = status; return this; }
        public PaymentBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public PaymentBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public PaymentBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Payment build() {
            return new Payment(id, bookingId, gateway, gatewayOrderId, gatewayPaymentId, upiTransactionId, amountCents, currency, status, idempotencyKey, createdAt, updatedAt);
        }
    }
}
