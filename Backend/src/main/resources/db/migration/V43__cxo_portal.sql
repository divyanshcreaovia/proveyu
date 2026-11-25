-- CXO portal: college leadership (principal, director, management) sign in to a separate portal and ask for dashboards in plain
-- language. A CXO account belongs to exactly one college and sees only that college's data; accounts are created by a platform admin.

ALTER TABLE users MODIFY COLUMN role
  ENUM('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN','COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER','CXO') NOT NULL DEFAULT 'CANDIDATE';

-- Which college a CXO account belongs to.
CREATE TABLE cxo_members (
  id            CHAR(36)     NOT NULL,
  college_id    CHAR(36)     NOT NULL,
  user_id       CHAR(36)     NOT NULL,
  designation   VARCHAR(150) NULL,                         -- e.g. Principal, Director, Chairman
  status        ENUM('ACTIVE','REVOKED') NOT NULL DEFAULT 'ACTIVE',
  created_by    CHAR(36)     NULL,                         -- platform admin who created the account
  last_login_at DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cxo_member_user (user_id),
  KEY idx_cxo_member_college (college_id),
  CONSTRAINT fk_cxo_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_cxo_member_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Dashboards a CXO saved: the spec only (metrics, charts, filters); data is always recomputed live from the database.
CREATE TABLE cxo_dashboards (
  id          CHAR(36)     NOT NULL,
  college_id  CHAR(36)     NOT NULL,
  user_id     CHAR(36)     NOT NULL,
  title       VARCHAR(200) NOT NULL,
  prompt      VARCHAR(2000) NULL,
  spec_json   MEDIUMTEXT   NOT NULL,
  pinned      TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cxo_dash_owner (college_id, user_id, updated_at),
  CONSTRAINT fk_cxo_dash_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_cxo_dash_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Audit of every prompt: what was asked, the spec that came back, how it was produced (AI or rules) and how long it took.
CREATE TABLE cxo_prompt_log (
  id           CHAR(36)      NOT NULL,
  college_id   CHAR(36)      NOT NULL,
  user_id      CHAR(36)      NOT NULL,
  prompt       VARCHAR(2000) NOT NULL,
  spec_json    MEDIUMTEXT    NULL,
  source       VARCHAR(10)   NOT NULL,                     -- AI | RULES
  status       VARCHAR(10)   NOT NULL,                     -- OK | FAILED
  error        VARCHAR(500)  NULL,
  duration_ms  INT           NOT NULL DEFAULT 0,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cxo_log_college (college_id, created_at),
  KEY idx_cxo_log_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
