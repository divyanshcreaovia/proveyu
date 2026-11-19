-- Off-campus jobs imported every night from a job feed (Adzuna). Imported jobs arrive as drafts for the ProveYu team to review;
-- (source, source_id) keeps the same ad from being imported twice. source_posted_at is when the ad was posted on the feed.
ALTER TABLE off_campus_jobs
    ADD COLUMN source            VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' AFTER status,
    ADD COLUMN source_id         VARCHAR(100) NULL AFTER source,
    ADD COLUMN source_posted_at  DATETIME     NULL AFTER source_id,
    ADD UNIQUE KEY uq_ocj_source (source, source_id),
    ADD INDEX idx_ocj_source_status (source, status, source_posted_at);

-- A student with an off-campus offer asks the placement cell for a No Objection Certificate, attaching the offer letter. The
-- placement cell approves (ProveYu generates the NOC PDF, or the TPO uploads a signed one) or rejects with a reason.
CREATE TABLE IF NOT EXISTS off_campus_noc_requests (
    id               CHAR(36)      NOT NULL,
    application_id   CHAR(36)      NOT NULL,
    job_id           CHAR(36)      NOT NULL,
    student_id       CHAR(36)      NOT NULL,
    user_id          CHAR(36)      NOT NULL,
    college_id       CHAR(36)      NOT NULL,
    offer_file_id    CHAR(36)      NOT NULL,
    student_note     VARCHAR(1000) NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    decision_note    VARCHAR(1000) NULL,
    noc_file_id      CHAR(36)      NULL,
    noc_generated    TINYINT(1)    NOT NULL DEFAULT 0,
    decided_by       CHAR(36)      NULL,
    decided_by_name  VARCHAR(150)  NULL,
    decided_at       DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_noc_college_status (college_id, status, created_at),
    INDEX idx_noc_application (application_id),
    CONSTRAINT fk_noc_application FOREIGN KEY (application_id) REFERENCES off_campus_applications (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
