package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static com.proveyu.college.domain.CollegeEnums.RoundResultValue;

/** An interviewer's rating of one student in one round. Saving it also sets the shared round result. */
@Entity
@Table(name = "college_round_scorecards")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RoundScorecard extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "round_id", columnDefinition = "CHAR(36)")
    private UUID roundId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "application_id", columnDefinition = "CHAR(36)")
    private UUID applicationId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private UUID userId;
    @Column(name = "interviewer_name")
    private String interviewerName;
    @Column(name = "technical")
    private Integer technical;
    @Column(name = "communication")
    private Integer communication;
    @Column(name = "attitude")
    private Integer attitude;
    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private RoundResultValue result;
    @Column(name = "remarks")
    private String remarks;
}
