-- V22: Preparation (mock tests) for students.
-- A test is uploaded by the platform admin as one JSON definition (sections, MCQs with answers, coding problems with
-- hidden test cases). Answers and hidden tests stay server-side; students get a stripped copy when they start.

CREATE TABLE IF NOT EXISTS prep_tests (
    id                CHAR(36)     NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       TEXT         NULL,
    company_tag       VARCHAR(100) NULL,
    duration_minutes  INT          NOT NULL,
    question_count    INT          NOT NULL DEFAULT 0,
    coding_count      INT          NOT NULL DEFAULT 0,
    total_marks       INT          NOT NULL DEFAULT 0,
    definition_json   MEDIUMTEXT   NOT NULL,
    published         TINYINT(1)   NOT NULL DEFAULT 0,
    created_by        CHAR(36)     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_prep_tests_published (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prep_attempts (
    id            CHAR(36)     NOT NULL,
    test_id       CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    started_at    DATETIME     NOT NULL,
    deadline_at   DATETIME     NOT NULL,
    submitted_at  DATETIME     NULL,
    score         DECIMAL(8,2) NULL,
    max_score     INT          NOT NULL DEFAULT 0,
    answers_json  MEDIUMTEXT   NULL,
    result_json   MEDIUMTEXT   NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_prep_attempts_user (user_id, test_id, started_at),
    CONSTRAINT fk_prep_attempt_test FOREIGN KEY (test_id) REFERENCES prep_tests(id) ON DELETE CASCADE,
    CONSTRAINT fk_prep_attempt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
