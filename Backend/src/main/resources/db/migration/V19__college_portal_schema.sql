-- =====================================================================
-- V19__college_portal_schema.sql
-- Consolidated College Placement Management schema.
-- Replaces the two parallel legacy models (student/student_profiles,
-- placement_drive/placement_drives, ...). Legacy data is copied in V20.
-- Every tenant table carries college_id (-> organizations.id) + index.
-- =====================================================================

-- 1. New college roles on users
ALTER TABLE users MODIFY COLUMN role
    ENUM('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN','COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER')
    NOT NULL DEFAULT 'CANDIDATE';

-- 2. College profile, approval state and placement policy (1:1 with organizations)
CREATE TABLE college_profiles (
    college_id                 CHAR(36)     NOT NULL,
    college_name               VARCHAR(200) NOT NULL,
    aicte_code                 VARCHAR(50)  NULL,
    university                 VARCHAR(200) NULL,
    city                       VARCHAR(100) NULL,
    state                      VARCHAR(100) NULL,
    email_domain               VARCHAR(120) NULL,
    website                    VARCHAR(255) NULL,
    logo_file_id               CHAR(36)     NULL,
    tpo_name                   VARCHAR(150) NULL,
    tpo_email                  VARCHAR(190) NULL,
    tpo_phone                  VARCHAR(20)  NULL,
    approval_status            ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    approval_note              VARCHAR(500) NULL,
    current_academic_year      VARCHAR(20)  NULL,
    -- placement policy
    policy_mode                ENUM('OPEN','ONE_OFFER','TIER_UPGRADE') NOT NULL DEFAULT 'TIER_UPGRADE',
    max_applications           INT          NULL,
    max_offers                 INT          NOT NULL DEFAULT 2,
    upgrade_min_hike_percent   DECIMAL(5,2) NOT NULL DEFAULT 0,
    block_after_accept         TINYINT(1)   NOT NULL DEFAULT 1,
    brand_color                VARCHAR(20)  NULL,
    created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (college_id),
    UNIQUE KEY uq_college_aicte (aicte_code),
    INDEX idx_college_approval (approval_status),
    CONSTRAINT fk_cp_org FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Branches / departments
CREATE TABLE college_branches (
    id          CHAR(36)     NOT NULL,
    college_id  CHAR(36)     NOT NULL,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cb_code (college_id, code),
    INDEX idx_cb_college (college_id),
    CONSTRAINT fk_cb_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Batches (graduation years)
CREATE TABLE college_batches (
    id          CHAR(36)    NOT NULL,
    college_id  CHAR(36)    NOT NULL,
    grad_year   INT         NOT NULL,
    label       VARCHAR(50) NOT NULL,
    active      TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_year (college_id, grad_year),
    INDEX idx_batch_college (college_id),
    CONSTRAINT fk_batch_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Tiers (Normal / Dream / Super Dream) based on CTC
CREATE TABLE college_tiers (
    id           CHAR(36)     NOT NULL,
    college_id   CHAR(36)     NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    tier_rank    INT          NOT NULL,          -- 1 = lowest (Normal), higher = better
    min_ctc_lpa  DECIMAL(8,2) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tier_rank (college_id, tier_rank),
    UNIQUE KEY uq_tier_name (college_id, name),
    INDEX idx_tier_college (college_id),
    CONSTRAINT fk_tier2_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Team membership (tenant resolution source of truth)
CREATE TABLE college_members (
    id            CHAR(36)     NOT NULL,
    college_id    CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    role          ENUM('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER') NOT NULL,
    designation   VARCHAR(150) NULL,
    branch_scope  TEXT         NULL,        -- comma separated college_branches.id; NULL = all branches
    status        ENUM('ACTIVE','REVOKED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_user (user_id),
    INDEX idx_member_college (college_id),
    CONSTRAINT fk_member_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_invites (
    id            CHAR(36)     NOT NULL,
    college_id    CHAR(36)     NOT NULL,
    email         VARCHAR(190) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          ENUM('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER') NOT NULL,
    designation   VARCHAR(150) NULL,
    branch_scope  TEXT         NULL,
    token_hash    CHAR(64)     NOT NULL,
    expires_at    DATETIME     NOT NULL,
    accepted_at   DATETIME     NULL,
    revoked_at    DATETIME     NULL,
    invited_by    CHAR(36)     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_invite_token (token_hash),
    INDEX idx_invite_college (college_id),
    CONSTRAINT fk_invite_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Email verification tokens (any user)
CREATE TABLE email_verification_tokens (
    id          CHAR(36)  NOT NULL,
    user_id     CHAR(36)  NOT NULL,
    token_hash  CHAR(64)  NOT NULL,
    expires_at  DATETIME  NOT NULL,
    used_at     DATETIME  NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_evt_token (token_hash),
    INDEX idx_evt_user (user_id),
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Stored files (logos, offer letters, documents, imports)
CREATE TABLE college_files (
    id             CHAR(36)     NOT NULL,
    college_id     CHAR(36)     NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    stored_name    VARCHAR(255) NOT NULL,
    content_type   VARCHAR(120) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    category       VARCHAR(40)  NOT NULL,
    uploaded_by    CHAR(36)     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_file_stored (stored_name),
    INDEX idx_file_college (college_id),
    CONSTRAINT fk_file_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. Students (single consolidated model)
CREATE TABLE college_students (
    id               CHAR(36)     NOT NULL,
    college_id       CHAR(36)     NOT NULL,
    user_id          CHAR(36)     NULL,
    roll_number      VARCHAR(50)  NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NULL,
    email            VARCHAR(190) NOT NULL,
    phone            VARCHAR(20)  NULL,
    gender           VARCHAR(20)  NULL,
    dob              DATE         NULL,
    branch_id        CHAR(36)     NULL,
    batch_year       INT          NOT NULL,
    degree           VARCHAR(80)  NULL,
    cgpa             DECIMAL(4,2) NULL,
    tenth_percent    DECIMAL(5,2) NULL,
    twelfth_percent  DECIMAL(5,2) NULL,
    active_backlogs  INT          NOT NULL DEFAULT 0,
    gap_years        INT          NOT NULL DEFAULT 0,
    skills           TEXT         NULL,
    linkedin_url     VARCHAR(255) NULL,
    github_url       VARCHAR(255) NULL,
    status           ENUM('ACTIVE','PLACED','DEBARRED','OPTED_OUT') NOT NULL DEFAULT 'ACTIVE',
    highest_ctc_lpa  DECIMAL(8,2) NULL,
    highest_tier_rank INT         NULL,
    deleted_at       DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cs_roll (college_id, roll_number),
    UNIQUE KEY uq_cs_email (college_id, email),
    INDEX idx_cs_college (college_id),
    INDEX idx_cs_branch (college_id, branch_id),
    INDEX idx_cs_batch (college_id, batch_year),
    INDEX idx_cs_status (college_id, status),
    INDEX idx_cs_user (user_id),
    CONSTRAINT fk_cs_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_cs_branch FOREIGN KEY (branch_id) REFERENCES college_branches(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_student_documents (
    id                   CHAR(36)    NOT NULL,
    college_id           CHAR(36)    NOT NULL,
    student_id           CHAR(36)    NOT NULL,
    doc_type             ENUM('RESUME','MARKSHEET_10','MARKSHEET_12','MARKSHEET_SEM','ID_PROOF','OTHER') NOT NULL,
    file_id              CHAR(36)    NOT NULL,
    verification_status  ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
    remarks              VARCHAR(500) NULL,
    verified_by          CHAR(36)    NULL,
    verified_at          DATETIME    NULL,
    created_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_csd_college (college_id),
    INDEX idx_csd_student (student_id),
    CONSTRAINT fk_csd_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_csd_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE,
    CONSTRAINT fk_csd_file FOREIGN KEY (file_id) REFERENCES college_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_import_jobs (
    id             CHAR(36)     NOT NULL,
    college_id     CHAR(36)     NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    dry_run        TINYINT(1)   NOT NULL DEFAULT 0,
    total_rows     INT          NOT NULL DEFAULT 0,
    created_rows   INT          NOT NULL DEFAULT 0,
    updated_rows   INT          NOT NULL DEFAULT 0,
    error_rows     INT          NOT NULL DEFAULT 0,
    duplicate_rows INT          NOT NULL DEFAULT 0,
    errors_json    MEDIUMTEXT   NULL,
    created_by     CHAR(36)     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cij_college (college_id, created_at),
    CONSTRAINT fk_cij_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. Companies
CREATE TABLE college_companies (
    id                    CHAR(36)     NOT NULL,
    college_id            CHAR(36)     NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    industry              VARCHAR(100) NULL,
    website               VARCHAR(255) NULL,
    hq_location           VARCHAR(150) NULL,
    mou_status            ENUM('NONE','IN_PROGRESS','SIGNED','EXPIRED') NOT NULL DEFAULT 'NONE',
    mou_expiry            DATE         NULL,
    linked_recruiter_id   CHAR(36)     NULL,
    notes                 TEXT         NULL,
    deleted_at            DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cc_name (college_id, name),
    INDEX idx_cc_college (college_id),
    CONSTRAINT fk_cc_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cc_recruiter FOREIGN KEY (linked_recruiter_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_company_contacts (
    id           CHAR(36)     NOT NULL,
    college_id   CHAR(36)     NOT NULL,
    company_id   CHAR(36)     NOT NULL,
    name         VARCHAR(150) NOT NULL,
    designation  VARCHAR(150) NULL,
    email        VARCHAR(190) NULL,
    phone        VARCHAR(20)  NULL,
    is_primary   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ccc_college (college_id),
    INDEX idx_ccc_company (company_id),
    CONSTRAINT fk_ccc_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ccc_company FOREIGN KEY (company_id) REFERENCES college_companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. Drives (eligibility criteria inline)
CREATE TABLE college_drives (
    id                     CHAR(36)     NOT NULL,
    college_id             CHAR(36)     NOT NULL,
    company_id             CHAR(36)     NOT NULL,
    title                  VARCHAR(200) NOT NULL,
    role_title             VARCHAR(150) NOT NULL,
    drive_type             ENUM('ON_CAMPUS','OFF_CAMPUS','POOL','INTERNSHIP','PPO') NOT NULL DEFAULT 'ON_CAMPUS',
    ctc_lpa                DECIMAL(8,2) NULL,
    stipend_monthly        DECIMAL(10,2) NULL,
    ctc_breakup            TEXT         NULL,
    location               VARCHAR(200) NULL,
    bond_months            INT          NOT NULL DEFAULT 0,
    bond_details           VARCHAR(500) NULL,
    job_description        TEXT         NULL,
    skills                 TEXT         NULL,
    mode                   ENUM('OFFLINE','ONLINE','HYBRID') NOT NULL DEFAULT 'OFFLINE',
    venue                  VARCHAR(255) NULL,
    registration_deadline  DATETIME     NULL,
    drive_date             DATE         NULL,
    tier_id                CHAR(36)     NULL,
    status                 ENUM('DRAFT','PUBLISHED','ONGOING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    -- eligibility (NULL = not applied)
    min_cgpa               DECIMAL(4,2) NULL,
    min_tenth              DECIMAL(5,2) NULL,
    min_twelfth            DECIMAL(5,2) NULL,
    max_active_backlogs    INT          NULL,
    max_gap_years          INT          NULL,
    batch_years            VARCHAR(100) NULL,     -- csv of grad years, NULL = any
    branch_ids             TEXT         NULL,     -- csv of college_branches.id, NULL = all
    required_skills        TEXT         NULL,     -- csv; student must have all
    published_at           DATETIME     NULL,
    created_by             CHAR(36)     NULL,
    deleted_at             DATETIME     NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cd_college (college_id),
    INDEX idx_cd_status (college_id, status),
    INDEX idx_cd_company (company_id),
    INDEX idx_cd_date (college_id, drive_date),
    CONSTRAINT fk_cd_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cd_company FOREIGN KEY (company_id) REFERENCES college_companies(id),
    CONSTRAINT fk_cd_tier FOREIGN KEY (tier_id) REFERENCES college_tiers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_eligibility_overrides (
    id          CHAR(36)     NOT NULL,
    college_id  CHAR(36)     NOT NULL,
    drive_id    CHAR(36)     NOT NULL,
    student_id  CHAR(36)     NOT NULL,
    action      ENUM('INCLUDE','EXCLUDE') NOT NULL,
    reason      VARCHAR(500) NOT NULL,
    created_by  CHAR(36)     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ceo (drive_id, student_id),
    INDEX idx_ceo_college (college_id),
    CONSTRAINT fk_ceo_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ceo_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_ceo_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. Applications
CREATE TABLE college_applications (
    id               CHAR(36)    NOT NULL,
    college_id       CHAR(36)    NOT NULL,
    drive_id         CHAR(36)    NOT NULL,
    student_id       CHAR(36)    NOT NULL,
    status           ENUM('APPLIED','SHORTLISTED','WAITLISTED','IN_PROCESS','SELECTED','REJECTED','WITHDRAWN') NOT NULL DEFAULT 'APPLIED',
    source           ENUM('SELF','TPO','OVERRIDE') NOT NULL DEFAULT 'SELF',
    current_round    INT         NOT NULL DEFAULT 0,
    remarks          VARCHAR(500) NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ca (drive_id, student_id),
    INDEX idx_ca_college (college_id),
    INDEX idx_ca_student (student_id),
    INDEX idx_ca_status (drive_id, status),
    CONSTRAINT fk_ca_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ca_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_ca_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. Rounds & results
CREATE TABLE college_drive_rounds (
    id            CHAR(36)     NOT NULL,
    college_id    CHAR(36)     NOT NULL,
    drive_id      CHAR(36)     NOT NULL,
    seq           INT          NOT NULL,
    name          VARCHAR(120) NOT NULL,
    round_type    ENUM('APTITUDE','CODING','GD','TECHNICAL','HR','CUSTOM') NOT NULL,
    scheduled_at  DATETIME     NULL,
    status        ENUM('PENDING','ONGOING','COMPLETED') NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cdr_seq (drive_id, seq),
    INDEX idx_cdr_college (college_id),
    CONSTRAINT fk_cdr_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cdr_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE college_round_results (
    id              CHAR(36)     NOT NULL,
    college_id      CHAR(36)     NOT NULL,
    round_id        CHAR(36)     NOT NULL,
    application_id  CHAR(36)     NOT NULL,
    result          ENUM('PENDING','PASS','FAIL','ABSENT','HOLD') NOT NULL DEFAULT 'PENDING',
    score           DECIMAL(6,2) NULL,
    remarks         VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_crr (round_id, application_id),
    INDEX idx_crr_college (college_id),
    CONSTRAINT fk_crr_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_crr_round FOREIGN KEY (round_id) REFERENCES college_drive_rounds(id) ON DELETE CASCADE,
    CONSTRAINT fk_crr_app FOREIGN KEY (application_id) REFERENCES college_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. Interview scheduling
CREATE TABLE college_interview_slots (
    id                CHAR(36)     NOT NULL,
    college_id        CHAR(36)     NOT NULL,
    drive_id          CHAR(36)     NOT NULL,
    round_id          CHAR(36)     NULL,
    application_id    CHAR(36)     NOT NULL,
    panel_name        VARCHAR(150) NULL,
    panel_members     VARCHAR(500) NULL,
    starts_at         DATETIME     NOT NULL,
    ends_at           DATETIME     NOT NULL,
    room              VARCHAR(100) NULL,
    meeting_link      VARCHAR(500) NULL,
    status            ENUM('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW') NOT NULL DEFAULT 'SCHEDULED',
    reschedule_count  INT          NOT NULL DEFAULT 0,
    reminder_sent_at  DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cis_college_time (college_id, starts_at),
    INDEX idx_cis_app (application_id),
    CONSTRAINT fk_cis_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cis_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_cis_round FOREIGN KEY (round_id) REFERENCES college_drive_rounds(id) ON DELETE SET NULL,
    CONSTRAINT fk_cis_app FOREIGN KEY (application_id) REFERENCES college_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. Offers
CREATE TABLE college_offers (
    id                    CHAR(36)     NOT NULL,
    college_id            CHAR(36)     NOT NULL,
    student_id            CHAR(36)     NOT NULL,
    drive_id              CHAR(36)     NULL,
    company_id            CHAR(36)     NOT NULL,
    role_title            VARCHAR(150) NOT NULL,
    ctc_lpa               DECIMAL(8,2) NOT NULL,
    ctc_breakup           TEXT         NULL,
    tier_rank             INT          NULL,
    offer_date            DATE         NULL,
    joining_date          DATE         NULL,
    status                ENUM('OFFERED','ACCEPTED','DECLINED','JOINED','REVOKED') NOT NULL DEFAULT 'OFFERED',
    offer_letter_file_id  CHAR(36)     NULL,
    remarks               VARCHAR(500) NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_co_college (college_id),
    INDEX idx_co_student (student_id),
    INDEX idx_co_status (college_id, status),
    CONSTRAINT fk_co_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_co_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE,
    CONSTRAINT fk_co_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE SET NULL,
    CONSTRAINT fk_co_company FOREIGN KEY (company_id) REFERENCES college_companies(id),
    CONSTRAINT fk_co_file FOREIGN KEY (offer_letter_file_id) REFERENCES college_files(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. Company feedback
CREATE TABLE college_company_feedback (
    id          CHAR(36)     NOT NULL,
    college_id  CHAR(36)     NOT NULL,
    company_id  CHAR(36)     NOT NULL,
    drive_id    CHAR(36)     NULL,
    rating      TINYINT      NOT NULL,
    comment     VARCHAR(1000) NULL,
    created_by  CHAR(36)     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ccf_college (college_id),
    INDEX idx_ccf_company (company_id),
    CONSTRAINT chk_ccf_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_ccf_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ccf_company FOREIGN KEY (company_id) REFERENCES college_companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_ccf_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. Announcements
CREATE TABLE college_announcements (
    id               CHAR(36)     NOT NULL,
    college_id       CHAR(36)     NOT NULL,
    title            VARCHAR(200) NOT NULL,
    body             TEXT         NOT NULL,
    filter_json      TEXT         NULL,
    recipient_count  INT          NOT NULL DEFAULT 0,
    send_email       TINYINT(1)   NOT NULL DEFAULT 0,
    created_by       CHAR(36)     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_can_college (college_id, created_at),
    CONSTRAINT fk_can_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. Email templates
CREATE TABLE college_email_templates (
    id            CHAR(36)     NOT NULL,
    college_id    CHAR(36)     NOT NULL,
    template_key  VARCHAR(60)  NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    body          TEXT         NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cet (college_id, template_key),
    CONSTRAINT fk_cet_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. Scheduled report exports
CREATE TABLE college_scheduled_reports (
    id            CHAR(36)     NOT NULL,
    college_id    CHAR(36)     NOT NULL,
    report_type   VARCHAR(40)  NOT NULL,
    format        ENUM('XLSX','PDF','CSV') NOT NULL DEFAULT 'XLSX',
    frequency     ENUM('DAILY','WEEKLY','MONTHLY') NOT NULL,
    recipients    VARCHAR(1000) NOT NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    last_run_at   DATETIME     NULL,
    next_run_at   DATETIME     NOT NULL,
    created_by    CHAR(36)     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_csr_college (college_id),
    INDEX idx_csr_next (active, next_run_at),
    CONSTRAINT fk_csr_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. Audit log
CREATE TABLE college_audit_logs (
    id             CHAR(36)     NOT NULL,
    college_id     CHAR(36)     NOT NULL,
    actor_user_id  CHAR(36)     NULL,
    actor_name     VARCHAR(150) NULL,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(60)  NOT NULL,
    entity_id      CHAR(36)     NULL,
    summary        VARCHAR(500) NOT NULL,
    details        TEXT         NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cal_college (college_id, created_at),
    INDEX idx_cal_entity (college_id, entity_type, entity_id),
    CONSTRAINT fk_cal_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
