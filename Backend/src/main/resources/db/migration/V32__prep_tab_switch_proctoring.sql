-- Mock test proctoring: every time a student leaves the test (switches tab or app, or stops sharing the screen) a row is kept
-- with the time and a screenshot of the shared screen as proof. The first switches are warnings; the next one submits the test.
CREATE TABLE IF NOT EXISTS prep_proctor_events (
    id              CHAR(36)     NOT NULL,
    attempt_id      CHAR(36)     NOT NULL,
    user_id         CHAR(36)     NOT NULL,
    kind            VARCHAR(20)  NOT NULL,              -- TAB_HIDDEN, WINDOW_BLUR, SHARE_STOPPED
    seq             INT          NOT NULL,              -- 1st, 2nd, 3rd switch in this attempt
    occurred_at     DATETIME(3)  NOT NULL,              -- server time the switch was reported
    client_at       DATETIME(3)  NULL,                  -- time on the student's computer, for reference
    screenshot_path VARCHAR(255) NULL,                  -- relative to prep.proctor-dir; NULL if no frame could be captured
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prep_proctor_seq (attempt_id, seq),
    INDEX idx_prep_proctor_attempt (attempt_id, occurred_at),
    CONSTRAINT fk_prep_proctor_attempt FOREIGN KEY (attempt_id) REFERENCES prep_attempts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Why the attempt was submitted when the student did not press Submit (NULL = submitted by the student or by the timer).
ALTER TABLE prep_attempts
    ADD COLUMN auto_submit_reason VARCHAR(30) NULL AFTER submitted_at;
