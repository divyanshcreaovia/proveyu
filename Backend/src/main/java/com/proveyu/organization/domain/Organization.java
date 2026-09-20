package com.proveyu.organization.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID userId;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false)
    private OrgType orgType = OrgType.COLLEGE;

    @Column(name = "code_or_gstin", length = 50)
    private String codeOrGstin;

    @Column(name = "contact_email", length = 190)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Organization() {}

    public Organization(UUID id, UUID userId, String legalName, OrgType orgType, String codeOrGstin, String contactEmail, String contactPhone, String address, boolean verified, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.legalName = legalName;
        this.orgType = orgType != null ? orgType : OrgType.COLLEGE;
        this.codeOrGstin = codeOrGstin;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.verified = verified;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public OrgType getOrgType() { return orgType; }
    public void setOrgType(OrgType orgType) { this.orgType = orgType; }

    public String getCodeOrGstin() { return codeOrGstin; }
    public void setCodeOrGstin(String codeOrGstin) { this.codeOrGstin = codeOrGstin; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static OrganizationBuilder builder() { return new OrganizationBuilder(); }

    public static class OrganizationBuilder {
        private UUID id;
        private UUID userId;
        private String legalName;
        private OrgType orgType = OrgType.COLLEGE;
        private String codeOrGstin;
        private String contactEmail;
        private String contactPhone;
        private String address;
        private boolean verified = true;
        private Instant createdAt;

        public OrganizationBuilder id(UUID id) { this.id = id; return this; }
        public OrganizationBuilder userId(UUID userId) { this.userId = userId; return this; }
        public OrganizationBuilder legalName(String legalName) { this.legalName = legalName; return this; }
        public OrganizationBuilder orgType(OrgType orgType) { this.orgType = orgType; return this; }
        public OrganizationBuilder codeOrGstin(String codeOrGstin) { this.codeOrGstin = codeOrGstin; return this; }
        public OrganizationBuilder contactEmail(String contactEmail) { this.contactEmail = contactEmail; return this; }
        public OrganizationBuilder contactPhone(String contactPhone) { this.contactPhone = contactPhone; return this; }
        public OrganizationBuilder address(String address) { this.address = address; return this; }
        public OrganizationBuilder verified(boolean verified) { this.verified = verified; return this; }
        public OrganizationBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Organization build() {
            return new Organization(id, userId, legalName, orgType, codeOrGstin, contactEmail, contactPhone, address, verified, createdAt);
        }
    }
}
