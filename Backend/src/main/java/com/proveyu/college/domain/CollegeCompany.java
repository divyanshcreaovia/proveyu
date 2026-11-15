package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "college_companies")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeCompany extends TenantEntity {
    @Column(name = "name")
    private String name;
    @Column(name = "industry")
    private String industry;
    @Column(name = "website")
    private String website;
    @Column(name = "hq_location")
    private String hqLocation;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "mou_status")
    private MouStatus mouStatus = MouStatus.NONE;
    @Column(name = "mou_expiry")
    private LocalDate mouExpiry;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "linked_recruiter_id", columnDefinition = "CHAR(36)")
    private UUID linkedRecruiterId;
    @Column(name = "notes")
    private String notes;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
