package com.proveyu.auth.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(unique = true, length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    public User(UUID id, String email, String phone, String passwordHash, Role role, String fullName, boolean active, Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.active = active;
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public Instant getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public void setResetTokenExpiresAt(Instant resetTokenExpiresAt) { this.resetTokenExpiresAt = resetTokenExpiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private UUID id;
        private String email;
        private String phone;
        private String passwordHash;
        private Role role;
        private String fullName;
        private boolean active = true;
        private Instant emailVerifiedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public UserBuilder id(UUID id) { this.id = id; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder phone(String phone) { this.phone = phone; return this; }
        public UserBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public UserBuilder role(Role role) { this.role = role; return this; }
        public UserBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public UserBuilder active(boolean active) { this.active = active; return this; }
        public UserBuilder emailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; return this; }
        public UserBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public UserBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public User build() {
            return new User(id, email, phone, passwordHash, role, fullName, active, emailVerifiedAt, createdAt, updatedAt);
        }
    }
}
