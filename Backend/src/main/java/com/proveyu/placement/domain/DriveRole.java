package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "drive_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveRole {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_role_id", columnDefinition = "CHAR(36)")
    private UUID driveRoleId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID driveId;

    @Column(name = "role_name", nullable = false, length = 150)
    private String roleName;
}
