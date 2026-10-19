-- =====================================================================
-- Migration: V14__placement_module.sql
-- Placement Module Schema adapted to PROVEYU (MySQL 8.0)
-- Links:
--   college_id -> organizations(id)
--   student_id -> users(id)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. company (Companies participating in placement drives)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS company (
    company_id    CHAR(36)        NOT NULL,
    name          VARCHAR(255)    NOT NULL,
    category      VARCHAR(100)    NULL,          -- IT, Core, Finance, etc.
    website       VARCHAR(255)    NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id),
    UNIQUE KEY uq_placement_company_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. branch (College-specific Branches / Departments)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS branch (
    branch_id     CHAR(36)        NOT NULL,
    college_id    CHAR(36)        NOT NULL,
    branch_name   VARCHAR(100)    NOT NULL,
    branch_code   VARCHAR(50)     NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (branch_id),
    UNIQUE KEY uq_college_branch_code (college_id, branch_code),
    INDEX idx_branch_college (college_id),
    CONSTRAINT fk_branch_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. student (Student Academic & Placement Metadata)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student (
    student_id          CHAR(36)        NOT NULL,
    college_id          CHAR(36)        NOT NULL,
    branch_id           CHAR(36)        NOT NULL,
    roll_number         VARCHAR(50)     NULL,
    cgpa                DECIMAL(4,2)    NULL,
    score               DECIMAL(5,2)    NULL,   -- assessment score used for Tier derivation
    tenth_percent       DECIMAL(5,2)    NULL,
    twelfth_percent     DECIMAL(5,2)    NULL,
    active_backlogs     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    gap_years           TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    placed_status       ENUM('UNPLACED','PLACED') NOT NULL DEFAULT 'UNPLACED',
    highest_package_lpa DECIMAL(6,2)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    INDEX idx_student_college (college_id),
    INDEX idx_student_branch (branch_id),
    INDEX idx_student_score (score),
    INDEX idx_student_placed (college_id, placed_status),
    CONSTRAINT fk_student_user    FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_branch  FOREIGN KEY (branch_id)  REFERENCES branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. tier (College-wise student classification by score)
--    tier_level: 1 = best. A student belongs to the LOWEST tier_level
--    (best tier) whose min_score he/she satisfies (score >= min_score).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tier (
    tier_id       CHAR(36)        NOT NULL,
    college_id    CHAR(36)        NOT NULL,
    tier_level    TINYINT UNSIGNED NOT NULL,     -- 1, 2, 3
    tier_name     VARCHAR(50)     NOT NULL,      -- 'Tier 1'
    min_score     DECIMAL(5,2)    NOT NULL,      -- 80, 70, 60
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tier_id),
    UNIQUE KEY uq_tier_college_level (college_id, tier_level),
    INDEX idx_tier_college (college_id),
    CONSTRAINT fk_tier_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. package_tier_rule (default package -> applicable tiers mapping)
--    One row per (package range, tier). max_package_lpa NULL = no upper limit.
--    Range is [min, max).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS package_tier_rule (
    rule_id          CHAR(36)        NOT NULL,
    college_id       CHAR(36)        NOT NULL,
    min_package_lpa  DECIMAL(6,2)    NOT NULL,
    max_package_lpa  DECIMAL(6,2)    NULL,
    tier_id          CHAR(36)        NOT NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id),
    INDEX idx_ptr_college (college_id),
    CONSTRAINT fk_ptr_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_ptr_tier    FOREIGN KEY (tier_id)    REFERENCES tier (tier_id) ON DELETE CASCADE,
    CONSTRAINT chk_ptr_range  CHECK (max_package_lpa IS NULL OR max_package_lpa > min_package_lpa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. placement_drive
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS placement_drive (
    drive_id         CHAR(36)        NOT NULL,
    college_id       CHAR(36)        NOT NULL,
    company_id       CHAR(36)        NOT NULL,
    offered_package  DECIMAL(6,2)    NOT NULL,   -- in LPA
    drive_date       DATE            NULL,
    location         VARCHAR(255)    NULL,
    placed_policy    ENUM('EXCLUDE_ALL','INCLUDE_ALL','ONLY_IF_HIGHER_PACKAGE')
                                     NOT NULL DEFAULT 'EXCLUDE_ALL',
    status           ENUM('DRAFT','LISTED','ONGOING','COMPLETED','CANCELLED')
                                     NOT NULL DEFAULT 'DRAFT',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (drive_id),
    INDEX idx_drive_college (college_id, drive_date),
    INDEX idx_drive_company (company_id),
    CONSTRAINT fk_drive_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_drive_company FOREIGN KEY (company_id) REFERENCES company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. drive_eligibility (1:1 with drive; NULL column = criterion not applied)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drive_eligibility (
    drive_id             CHAR(36)          NOT NULL,
    min_cgpa             DECIMAL(4,2)      NULL,
    min_score            DECIMAL(5,2)      NULL,   -- extra override on top of tier
    min_10th_percent     DECIMAL(5,2)      NULL,
    min_12th_percent     DECIMAL(5,2)      NULL,
    max_active_backlogs  SMALLINT UNSIGNED NULL,
    max_gap_years        TINYINT UNSIGNED  NULL,
    PRIMARY KEY (drive_id),
    CONSTRAINT fk_elig_drive FOREIGN KEY (drive_id) REFERENCES placement_drive (drive_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. drive_tier / drive_branch / drive_role (many-to-many / child entities)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drive_tier (
    drive_id  CHAR(36) NOT NULL,
    tier_id   CHAR(36) NOT NULL,
    PRIMARY KEY (drive_id, tier_id),
    CONSTRAINT fk_dt_drive FOREIGN KEY (drive_id) REFERENCES placement_drive (drive_id) ON DELETE CASCADE,
    CONSTRAINT fk_dt_tier  FOREIGN KEY (tier_id)  REFERENCES tier (tier_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drive_branch (
    drive_id   CHAR(36) NOT NULL,
    branch_id  CHAR(36) NOT NULL,
    PRIMARY KEY (drive_id, branch_id),
    CONSTRAINT fk_db_drive  FOREIGN KEY (drive_id)  REFERENCES placement_drive (drive_id) ON DELETE CASCADE,
    CONSTRAINT fk_db_branch FOREIGN KEY (branch_id) REFERENCES branch (branch_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drive_role (
    drive_role_id  CHAR(36)        NOT NULL,
    drive_id       CHAR(36)        NOT NULL,
    role_name      VARCHAR(150)    NOT NULL,
    PRIMARY KEY (drive_role_id),
    UNIQUE KEY uq_drive_role (drive_id, role_name),
    CONSTRAINT fk_dr_drive FOREIGN KEY (drive_id) REFERENCES placement_drive (drive_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. hiring_round (master list of round types)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hiring_round (
    round_id    CHAR(36)        NOT NULL,
    round_name  VARCHAR(100)    NOT NULL,
    PRIMARY KEY (round_id),
    UNIQUE KEY uq_round_name (round_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the 6 standard hiring rounds with deterministic UUIDs
INSERT IGNORE INTO hiring_round (round_id, round_name) VALUES
    ('11111111-1111-1111-1111-111111111101', 'Online Assessment'),
    ('11111111-1111-1111-1111-111111111102', 'Coding Assessment'),
    ('11111111-1111-1111-1111-111111111103', 'Group Discussion'),
    ('11111111-1111-1111-1111-111111111104', 'Technical Round 1'),
    ('11111111-1111-1111-1111-111111111105', 'Technical Round 2'),
    ('11111111-1111-1111-1111-111111111106', 'HR Round');

-- ---------------------------------------------------------------------
-- 10. drive_round (one row per round of a drive)
--     Counts are DERIVED from drive_round_student, not stored.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drive_round (
    drive_round_id  CHAR(36)          NOT NULL,
    drive_id        CHAR(36)          NOT NULL,
    round_id        CHAR(36)          NOT NULL,
    round_number    TINYINT UNSIGNED  NOT NULL,   -- order inside this drive: 1, 2, 3...
    round_date      DATE              NULL,
    status          ENUM('PENDING','ONGOING','COMPLETED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (drive_round_id),
    UNIQUE KEY uq_drive_round_number (drive_id, round_number),
    UNIQUE KEY uq_drive_round_type   (drive_id, round_id),
    CONSTRAINT fk_dround_drive FOREIGN KEY (drive_id) REFERENCES placement_drive (drive_id) ON DELETE CASCADE,
    CONSTRAINT fk_dround_round FOREIGN KEY (round_id) REFERENCES hiring_round (round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. drive_round_student (each student's status in each round)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drive_round_student (
    id              CHAR(36)        NOT NULL,
    drive_round_id  CHAR(36)        NOT NULL,
    student_id      CHAR(36)        NOT NULL,
    status          ENUM('SHORTLISTED','ABSENT','ATTENDED','SELECTED','REJECTED')
                                    NOT NULL DEFAULT 'SHORTLISTED',
    score           DECIMAL(6,2)    NULL,
    remarks         VARCHAR(500)    NULL,
    mail_sent_at    DATETIME        NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_round_student (drive_round_id, student_id),
    INDEX idx_drs_student (student_id),
    INDEX idx_drs_round_status (drive_round_id, status),
    CONSTRAINT fk_drs_round   FOREIGN KEY (drive_round_id) REFERENCES drive_round (drive_round_id) ON DELETE CASCADE,
    CONSTRAINT fk_drs_student FOREIGN KEY (student_id)     REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 12. placement_offer (final offers after last round)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS placement_offer (
    offer_id      CHAR(36)        NOT NULL,
    drive_id      CHAR(36)        NOT NULL,
    student_id    CHAR(36)        NOT NULL,
    role_name     VARCHAR(150)    NULL,
    package_lpa   DECIMAL(6,2)    NOT NULL,
    offer_status  ENUM('OFFERED','ACCEPTED','REJECTED') NOT NULL DEFAULT 'OFFERED',
    offered_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (offer_id),
    UNIQUE KEY uq_offer_drive_student (drive_id, student_id),
    INDEX idx_offer_student (student_id, offer_status),
    CONSTRAINT fk_offer_drive   FOREIGN KEY (drive_id)   REFERENCES placement_drive (drive_id) ON DELETE CASCADE,
    CONSTRAINT fk_offer_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
