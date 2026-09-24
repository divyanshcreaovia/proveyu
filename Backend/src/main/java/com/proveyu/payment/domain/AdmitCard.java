package com.proveyu.payment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admit_cards")
public class AdmitCard {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "booking_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private UUID bookingId;

    @Column(name = "qr_code_token", nullable = false, unique = true, length = 200)
    private String qrCodeToken;

    @Column(name = "barcode_value", nullable = false, unique = true, length = 100)
    private String barcodeValue;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    public AdmitCard() {}

    public AdmitCard(UUID id, UUID bookingId, String qrCodeToken, String barcodeValue, Instant issuedAt, String pdfUrl) {
        this.id = id;
        this.bookingId = bookingId;
        this.qrCodeToken = qrCodeToken;
        this.barcodeValue = barcodeValue;
        this.issuedAt = issuedAt;
        this.pdfUrl = pdfUrl;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public String getQrCodeToken() { return qrCodeToken; }
    public void setQrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; }

    public String getBarcodeValue() { return barcodeValue; }
    public void setBarcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public static AdmitCardBuilder builder() { return new AdmitCardBuilder(); }

    public static class AdmitCardBuilder {
        private UUID id;
        private UUID bookingId;
        private String qrCodeToken;
        private String barcodeValue;
        private Instant issuedAt;
        private String pdfUrl;

        public AdmitCardBuilder id(UUID id) { this.id = id; return this; }
        public AdmitCardBuilder bookingId(UUID bookingId) { this.bookingId = bookingId; return this; }
        public AdmitCardBuilder qrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; return this; }
        public AdmitCardBuilder barcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; return this; }
        public AdmitCardBuilder issuedAt(Instant issuedAt) { this.issuedAt = issuedAt; return this; }
        public AdmitCardBuilder pdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; return this; }

        public AdmitCard build() {
            return new AdmitCard(id, bookingId, qrCodeToken, barcodeValue, issuedAt, pdfUrl);
        }
    }
}
