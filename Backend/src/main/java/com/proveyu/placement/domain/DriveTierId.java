package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DriveTierId implements Serializable {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tier_id", columnDefinition = "CHAR(36)")
    private UUID tierId;
}
