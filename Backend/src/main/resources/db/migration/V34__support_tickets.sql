-- Help & support: a message a student or college staff member writes from the portal. The ProveYu team reads and answers it in the
-- admin portal; the answer reaches the user in the portal (and by e-mail if the admin chooses).
CREATE TABLE IF NOT EXISTS support_tickets (
    id            CHAR(36)      NOT NULL,
    user_id       CHAR(36)      NOT NULL,
    user_name     VARCHAR(150)  NOT NULL,
    user_email    VARCHAR(190)  NOT NULL,
    user_role     VARCHAR(30)   NOT NULL,
    portal        VARCHAR(20)   NOT NULL,
    college_id    CHAR(36)      NULL,
    college_name  VARCHAR(255)  NULL,
    category      VARCHAR(30)   NOT NULL,
    subject       VARCHAR(150)  NOT NULL,
    message       TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    reply         TEXT          NULL,
    replied_by    VARCHAR(150)  NULL,
    replied_at    DATETIME      NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_support_status_time (status, created_at),
    INDEX idx_support_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
