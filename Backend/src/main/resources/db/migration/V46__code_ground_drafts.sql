-- Code ground: the code a student is writing, per problem and language, so it is there on any computer (lab PCs are shared).
CREATE TABLE code_ground_drafts (
  user_id     CHAR(36)    NOT NULL,
  problem_key VARCHAR(80) NOT NULL,
  language    VARCHAR(12) NOT NULL,
  code        MEDIUMTEXT  NOT NULL,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, problem_key, language),
  CONSTRAINT fk_cgd_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
