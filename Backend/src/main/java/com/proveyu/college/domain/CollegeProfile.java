package com.proveyu.college.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.proveyu.college.domain.CollegeEnums.*;

/** One row per college (PK = organizations.id). Holds approval state, branding and placement policy. */
@Entity
@Table(name = "college_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeProfile {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", columnDefinition = "CHAR(36)")
    private UUID collegeId;
    @Column(name = "college_name") private String collegeName;
    @Column(name = "aicte_code") private String aicteCode;
    @Column(name = "university") private String university;
    @Column(name = "city") private String city;
    @Column(name = "state") private String state;
    @Column(name = "email_domain") private String emailDomain;
    @Column(name = "website") private String website;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "logo_file_id", columnDefinition = "CHAR(36)") private UUID logoFileId;
    @Column(name = "tpo_name") private String tpoName;
    @Column(name = "tpo_email") private String tpoEmail;
    @Column(name = "tpo_phone") private String tpoPhone;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status") private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    @Column(name = "approval_note") private String approvalNote;
    @Column(name = "current_academic_year") private String currentAcademicYear;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode") private PolicyMode policyMode = PolicyMode.TIER_UPGRADE;
    @Column(name = "max_applications") private Integer maxApplications;
    @Builder.Default
    @Column(name = "max_offers") private int maxOffers = 2;
    @Builder.Default
    @Column(name = "upgrade_min_hike_percent") private BigDecimal upgradeMinHikePercent = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "block_after_accept") private boolean blockAfterAccept = true;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "off_campus_policy") private com.proveyu.offcampus.domain.OffCampusModel.Policy offCampusPolicy =
            com.proveyu.offcampus.domain.OffCampusModel.Policy.ALLOW;
    @Column(name = "brand_color") private String brandColor;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;
}
