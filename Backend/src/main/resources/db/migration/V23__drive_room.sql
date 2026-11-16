-- V23: Drive Room. Company HR / panel members get access to ONE drive of ONE college.
-- Everything they change (shortlists, round results, offers) is written to the existing college_applications,
-- college_round_results and college_offers tables through the same services the TPO uses, so both portals stay in sync.

CREATE TABLE IF NOT EXISTS college_drive_recruiters (
    id              CHAR(36)     NOT NULL,
    college_id      CHAR(36)     NOT NULL,
    drive_id        CHAR(36)     NOT NULL,
    email           VARCHAR(190) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    designation     VARCHAR(150) NULL,
    user_id         CHAR(36)     NULL,           -- set when the invitation is accepted (a RECRUITER user)
    token_hash      CHAR(64)     NULL,           -- invitation token (SHA-256); cleared on accept
    expires_at      DATETIME     NULL,
    status          ENUM('INVITED','ACTIVE','REVOKED') NOT NULL DEFAULT 'INVITED',
    share_details   TINYINT(1)   NOT NULL DEFAULT 0,   -- student phone / e-mail / 10th / 12th marks visible to the company
    invited_by      CHAR(36)     NULL,
    accepted_at     DATETIME     NULL,
    last_access_at  DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cdrec_drive_email (drive_id, email),
    UNIQUE KEY uq_cdrec_token (token_hash),
    INDEX idx_cdrec_user (user_id),
    INDEX idx_cdrec_college (college_id),
    CONSTRAINT fk_cdrec_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cdrec_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE CASCADE,
    CONSTRAINT fk_cdrec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One scorecard per interviewer per student per round. Saving one also sets college_round_results (latest wins).
CREATE TABLE IF NOT EXISTS college_round_scorecards (
    id                CHAR(36)      NOT NULL,
    college_id        CHAR(36)      NOT NULL,
    round_id          CHAR(36)      NOT NULL,
    application_id    CHAR(36)      NOT NULL,
    user_id           CHAR(36)      NOT NULL,
    interviewer_name  VARCHAR(150)  NOT NULL,
    technical         TINYINT       NULL,
    communication     TINYINT       NULL,
    attitude          TINYINT       NULL,
    result            ENUM('PASS','FAIL','HOLD','ABSENT') NOT NULL,
    remarks           VARCHAR(1000) NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_crs (round_id, application_id, user_id),
    INDEX idx_crs_college (college_id),
    CONSTRAINT fk_crs_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_crs_round FOREIGN KEY (round_id) REFERENCES college_drive_rounds(id) ON DELETE CASCADE,
    CONSTRAINT fk_crs_app FOREIGN KEY (application_id) REFERENCES college_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
