package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "hiring_round")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HiringRound {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "round_id", columnDefinition = "CHAR(36)")
    private UUID roundId;

    @Column(name = "round_name", nullable = false, unique = true, length = 100)
    private String roundName;
}
