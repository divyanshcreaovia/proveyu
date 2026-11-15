package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static com.proveyu.college.domain.CollegeEnums.*;

@Entity
@Table(name = "college_interview_slots")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSlot extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "round_id", columnDefinition = "CHAR(36)")
    private UUID roundId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "application_id", columnDefinition = "CHAR(36)")
    private UUID applicationId;
    @Column(name = "panel_name")
    private String panelName;
    @Column(name = "panel_members")
    private String panelMembers;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "room")
    private String room;
    @Column(name = "meeting_link")
    private String meetingLink;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SlotStatus status = SlotStatus.SCHEDULED;
    @Builder.Default
    @Column(name = "reschedule_count")
    private int rescheduleCount = 0;
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;
}
