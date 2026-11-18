-- Platform admin portal.

-- Settings the admin changes from the portal (price, free interview days, UPI, mock test rules, website banner). A row overrides
-- the default from application.yml / .env; without a row the default applies.
CREATE TABLE IF NOT EXISTS platform_settings (
    setting_key   VARCHAR(80)   NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    updated_by    CHAR(36)      NULL,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- What each platform admin did (college decisions, user blocks, settings, broadcasts, payments, new admins).
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id          CHAR(36)      NOT NULL,
    admin_id    CHAR(36)      NULL,
    admin_name  VARCHAR(150)  NULL,
    action      VARCHAR(60)   NOT NULL,
    target_type VARCHAR(40)   NULL,
    target_id   VARCHAR(64)   NULL,
    summary     VARCHAR(500)  NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_admin_audit_time (created_at),
    INDEX idx_admin_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- An approved college can be suspended (its staff cannot sign in to the portal) and reactivated.
ALTER TABLE college_profiles
    MODIFY COLUMN approval_status ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING';
