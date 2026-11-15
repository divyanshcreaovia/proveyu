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
@Table(name = "college_student_documents")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDocument extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "student_id", columnDefinition = "CHAR(36)")
    private UUID studentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocType docType;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "file_id", columnDefinition = "CHAR(36)")
    private UUID fileId;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    @Column(name = "remarks")
    private String remarks;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "verified_by", columnDefinition = "CHAR(36)")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private Instant verifiedAt;
}
