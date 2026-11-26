-- Code ground (practice problems and topic learning guides): what each student has tried and finished.
-- The problems themselves are versioned files in resources/codeground; only progress is stored here.
CREATE TABLE code_ground_progress (
  user_id     CHAR(36)    NOT NULL,
  item_type   VARCHAR(10) NOT NULL,              -- PROBLEM | GUIDE
  item_key    VARCHAR(80) NOT NULL,              -- problem key, or topic key for its guide
  attempts    INT         NOT NULL DEFAULT 0,    -- test runs of a problem
  done_at     DATETIME    NULL,                  -- all tests passed / guide read to the end (null = tried, not done)
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, item_type, item_key),
  CONSTRAINT fk_cgp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
