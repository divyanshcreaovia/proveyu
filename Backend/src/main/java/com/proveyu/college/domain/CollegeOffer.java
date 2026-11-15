package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "college_offers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeOffer extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drive_id", columnDefinition = "CHAR(36)")
    private UUID driveId;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "company_id", columnDefinition = "CHAR(36)")
    private UUID companyId;
    @Column(name = "role_title")
    private String roleTitle;
    @Column(name = "ctc_lpa")
    private BigDecimal ctcLpa;
    @Column(name = "ctc_breakup")
    private String ctcBreakup;
    @Column(name = "tier_rank")
    private Integer tierRank;
    @Column(name = "offer_date")
    private LocalDate offerDate;
    @Column(name = "joining_date")
    private LocalDate joiningDate;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OfferStatus status = OfferStatus.OFFERED;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "offer_letter_file_id", columnDefinition = "CHAR(36)")
    private UUID offerLetterFileId;
    @Column(name = "remarks")
    private String remarks;
}
