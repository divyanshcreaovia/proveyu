package com.proveyu.shared.settings;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One setting changed by a platform admin; it overrides the application.yml default. */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {
    @Id
    @Column(name = "setting_key")
    private String key;
    @Column(name = "setting_value")
    private String value;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "updated_by", columnDefinition = "CHAR(36)")
    private UUID updatedBy;
    @Column(name = "updated_at")
    private Instant updatedAt;
}
