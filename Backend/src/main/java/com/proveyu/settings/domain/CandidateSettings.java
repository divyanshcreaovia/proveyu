package com.proveyu.settings.domain;

import com.proveyu.auth.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_settings")
@Getter
@Setter
public class CandidateSettings {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    @Column(name = "email_notifications")
    private boolean emailNotifications = true;

    @Column(name = "sms_notifications")
    private boolean smsNotifications = false;

    @Column(name = "push_notifications")
    private boolean pushNotifications = true;

    @Column(name = "test_reminders")
    private boolean testReminders = true;

    @Column(name = "interview_invites")
    private boolean interviewInvites = true;

    @Column(name = "application_updates")
    private boolean applicationUpdates = true;

    @Column(name = "job_offers")
    private boolean jobOffers = true;

    @Column(name = "marketing_updates")
    private boolean marketingUpdates = false;

    @Column(name = "newsletter")
    private boolean newsletter = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
