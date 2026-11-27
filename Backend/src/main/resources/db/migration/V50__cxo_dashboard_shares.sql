-- Read-only links to a saved CXO dashboard, for people without an account (trustees, directors). Only a hash of the link's secret is
-- stored, so a database read does not reveal working links. A link stops working when it expires or is revoked.
CREATE TABLE cxo_dashboard_shares (
  id             CHAR(36)    NOT NULL,
  dashboard_id   CHAR(36)    NOT NULL,
  college_id     CHAR(36)    NOT NULL,
  created_by     CHAR(36)    NOT NULL,
  token_hash     CHAR(64)    NOT NULL,                      -- SHA-256 of the secret in the link
  expires_at     DATETIME    NOT NULL,
  revoked_at     DATETIME    NULL,
  views          INT         NOT NULL DEFAULT 0,
  last_viewed_at DATETIME    NULL,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cxo_share_token (token_hash),
  KEY idx_cxo_share_dash (dashboard_id),
  CONSTRAINT fk_cxo_share_dash FOREIGN KEY (dashboard_id) REFERENCES cxo_dashboards (id) ON DELETE CASCADE,
  CONSTRAINT fk_cxo_share_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE,
  CONSTRAINT fk_cxo_share_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
