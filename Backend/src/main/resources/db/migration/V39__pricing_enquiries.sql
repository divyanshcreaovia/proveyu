-- Pricing page on the public website: a college or company asks for a quote, the ProveYu team calls them back.
-- Admins see every enquiry in Admin > Pricing enquiries and mark it contacted or closed.
CREATE TABLE IF NOT EXISTS pricing_enquiries (
    id               CHAR(36)      NOT NULL,
    full_name        VARCHAR(150)  NOT NULL,
    email            VARCHAR(190)  NOT NULL,
    phone            VARCHAR(20)   NOT NULL,
    organization     VARCHAR(255)  NOT NULL,
    org_type         VARCHAR(20)   NOT NULL,
    designation      VARCHAR(120)  NULL,
    city             VARCHAR(120)  NULL,
    student_count    VARCHAR(20)   NULL,
    message          TEXT          NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    admin_note       TEXT          NULL,
    handled_by       VARCHAR(150)  NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pricing_enquiry_status_time (status, created_at),
    INDEX idx_pricing_enquiry_email_time (email, created_at)
);
