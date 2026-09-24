package com.proveyu.verification.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(nullable = false, unique = true, length = 15)
    private String gstin;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(length = 80)
    private String state;

    @Column(name = "website_domain", length = 255)
    private String websiteDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_status", nullable = false)
    private GstStatus gstStatus = GstStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum GstStatus {
        ACTIVE, CANCELLED, SUSPENDED, UNKNOWN
    }

    public enum VerificationStatus {
        UNVERIFIED, PENDING, VERIFIED, REJECTED, SUSPENDED
    }

    public Company() {}

    public Company(UUID id, String legalName, String tradeName, String gstin, String registeredAddress, String state, String websiteDomain, GstStatus gstStatus, VerificationStatus verificationStatus, Instant verifiedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.gstin = gstin;
        this.registeredAddress = registeredAddress;
        this.state = state;
        this.websiteDomain = websiteDomain;
        this.gstStatus = gstStatus != null ? gstStatus : GstStatus.ACTIVE;
        this.verificationStatus = verificationStatus != null ? verificationStatus : VerificationStatus.UNVERIFIED;
        this.verifiedAt = verifiedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getWebsiteDomain() { return websiteDomain; }
    public void setWebsiteDomain(String websiteDomain) { this.websiteDomain = websiteDomain; }

    public GstStatus getGstStatus() { return gstStatus; }
    public void setGstStatus(GstStatus gstStatus) { this.gstStatus = gstStatus; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static CompanyBuilder builder() { return new CompanyBuilder(); }

    public static class CompanyBuilder {
        private UUID id;
        private String legalName;
        private String tradeName;
        private String gstin;
        private String registeredAddress;
        private String state;
        private String websiteDomain;
        private GstStatus gstStatus = GstStatus.ACTIVE;
        private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;
        private Instant verifiedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public CompanyBuilder id(UUID id) { this.id = id; return this; }
        public CompanyBuilder legalName(String legalName) { this.legalName = legalName; return this; }
        public CompanyBuilder tradeName(String tradeName) { this.tradeName = tradeName; return this; }
        public CompanyBuilder gstin(String gstin) { this.gstin = gstin; return this; }
        public CompanyBuilder registeredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; return this; }
        public CompanyBuilder state(String state) { this.state = state; return this; }
        public CompanyBuilder websiteDomain(String websiteDomain) { this.websiteDomain = websiteDomain; return this; }
        public CompanyBuilder gstStatus(GstStatus gstStatus) { this.gstStatus = gstStatus; return this; }
        public CompanyBuilder verificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; return this; }
        public CompanyBuilder verifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; return this; }
        public CompanyBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public CompanyBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Company build() {
            return new Company(id, legalName, tradeName, gstin, registeredAddress, state, websiteDomain, gstStatus, verificationStatus, verifiedAt, createdAt, updatedAt);
        }
    }
}
