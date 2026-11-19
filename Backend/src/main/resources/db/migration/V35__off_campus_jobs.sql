-- Off-campus jobs: the ProveYu team posts jobs from companies outside campus drives. Students of every registered college (filtered by
-- eligibility) see them in the student portal and apply on the company's own site. ProveYu records the click and the student keeps
-- the status up to date (applied, interviewing, offer), which the student's placement cell can see.
CREATE TABLE IF NOT EXISTS off_campus_jobs (
    id                 CHAR(36)      NOT NULL,
    company_name       VARCHAR(150)  NOT NULL,
    role_title         VARCHAR(150)  NOT NULL,
    job_type           VARCHAR(20)   NOT NULL,
    work_mode          VARCHAR(20)   NOT NULL,
    location           VARCHAR(150)  NULL,
    ctc_min_lpa        DECIMAL(8,2)  NULL,
    ctc_max_lpa        DECIMAL(8,2)  NULL,
    stipend_per_month  INT           NULL,
    description        TEXT          NULL,
    skills             VARCHAR(500)  NULL,
    apply_url          VARCHAR(1000) NOT NULL,
    apply_deadline     DATETIME      NULL,
    -- eligibility; blank = no restriction. Keyword lists are comma-separated and match case-insensitively inside the student's value.
    batch_years        VARCHAR(100)  NULL,
    degree_keywords    VARCHAR(300)  NULL,
    branch_keywords    VARCHAR(300)  NULL,
    min_cgpa           DECIMAL(4,2)  NULL,
    max_backlogs       INT           NULL,
    college_ids        TEXT          NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by         CHAR(36)      NULL,
    published_at       DATETIME      NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ocj_status_deadline (status, apply_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per student and job: created when the student saves the job or opens its apply link, then kept up to date by the student.
CREATE TABLE IF NOT EXISTS off_campus_applications (
    id              CHAR(36)      NOT NULL,
    job_id          CHAR(36)      NOT NULL,
    student_id      CHAR(36)      NOT NULL,
    user_id         CHAR(36)      NOT NULL,
    college_id      CHAR(36)      NOT NULL,
    status          VARCHAR(20)   NULL,
    saved           TINYINT(1)    NOT NULL DEFAULT 0,
    offer_ctc_lpa   DECIMAL(8,2)  NULL,
    note            VARCHAR(500)  NULL,
    clicked_at      DATETIME      NULL,
    applied_at      DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_oca_job_student (job_id, student_id),
    INDEX idx_oca_college_status (college_id, status),
    INDEX idx_oca_user (user_id),
    CONSTRAINT fk_oca_job FOREIGN KEY (job_id) REFERENCES off_campus_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_oca_student FOREIGN KEY (student_id) REFERENCES college_students (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The placement cell decides whether its students may use off-campus jobs: ALLOW, BLOCK_PLACED (placed students see but cannot apply)
-- or DISABLED (the page is hidden from its students).
ALTER TABLE college_profiles ADD COLUMN off_campus_policy ENUM('ALLOW','BLOCK_PLACED','DISABLED') NOT NULL DEFAULT 'ALLOW' AFTER block_after_accept;
