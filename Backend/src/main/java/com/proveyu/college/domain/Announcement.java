package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "college_announcements")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement extends TenantEntity {
    @Column(name = "title")
    private String title;
    @Column(name = "body")
    private String body;
    @Column(name = "filter_json")
    private String filterJson;
    @Builder.Default
    @Column(name = "recipient_count")
    private int recipientCount = 0;
    @Column(name = "send_email")
    private boolean sendEmail;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;
}
