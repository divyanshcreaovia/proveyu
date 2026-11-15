package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "college_round_results")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RoundResult extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "round_id", columnDefinition = "CHAR(36)")
    private UUID roundId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "application_id", columnDefinition = "CHAR(36)")
    private UUID applicationId;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private RoundResultValue result = RoundResultValue.PENDING;
    @Column(name = "score")
    private BigDecimal score;
    @Column(name = "remarks")
    private String remarks;
}
