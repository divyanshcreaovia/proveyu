package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row per student who received an announcement; read_at drives the student's unread count. */
@Entity
@Table(name = "college_announcement_recipients")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRecipient extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "announcement_id", columnDefinition = "CHAR(36)")
    private UUID announcementId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;
    @Column(name = "read_at")
    private Instant readAt;
}
