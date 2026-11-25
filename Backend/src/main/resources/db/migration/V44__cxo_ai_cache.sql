-- Cache of AI answers in the CXO portal, per college: the dashboard plan for a question (kind PLAN) and the summary for a set of
-- numbers (kind INSIGHT). Asking the same thing again renders from here without calling the model; the numbers themselves are
-- never cached (every widget is queried live).
CREATE TABLE cxo_ai_cache (
  id           CHAR(36)     NOT NULL,
  college_id   CHAR(36)     NOT NULL,
  kind         VARCHAR(10)  NOT NULL,          -- PLAN | INSIGHT
  cache_key    CHAR(64)     NOT NULL,          -- SHA-256 of the normalized question (+ current dashboard / numbers)
  payload      MEDIUMTEXT   NOT NULL,          -- JSON
  hits         INT          NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_hit_at  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cxo_cache (college_id, kind, cache_key),
  KEY idx_cxo_cache_age (created_at),
  CONSTRAINT fk_cxo_cache_college FOREIGN KEY (college_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
