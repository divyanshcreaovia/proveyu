-- AI mock interview: difficulty level per interview, and interviews bought by students (Razorpay).
-- Free interviews are no longer a one-time allowance: one free interview every N days (interview.free-every-days),
-- worked out from the student's last free interview, so no new column is needed for that.

ALTER TABLE ai_interview_sessions
    ADD COLUMN difficulty ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM' AFTER interview_type;

CREATE INDEX idx_ai_sessions_student_source ON ai_interview_sessions (student_id, credit_source, started_at);

CREATE TABLE IF NOT EXISTS interview_purchases (
    id                  CHAR(36)     NOT NULL,
    college_id          CHAR(36)     NOT NULL,
    student_id          CHAR(36)     NOT NULL,
    user_id             CHAR(36)     NOT NULL,
    quantity            INT          NOT NULL,
    amount_paise        INT          NOT NULL,              -- price fixed by the server when the order is created
    status              ENUM('PENDING','PAID','FAILED') NOT NULL DEFAULT 'PENDING',
    gateway_order_id    VARCHAR(100) NOT NULL,
    gateway_payment_id  VARCHAR(100) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at             DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_purchase_order (gateway_order_id),
    KEY idx_interview_purchase_student (student_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
