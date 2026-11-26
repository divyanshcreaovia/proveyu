-- Code ground: bonus points for the problem of the day, and the history of what a student submitted.
CREATE TABLE code_ground_daily (
  user_id     CHAR(36)    NOT NULL,
  day         DATE        NOT NULL,              -- the India date whose problem was solved
  problem_key VARCHAR(80) NOT NULL,
  points      INT         NOT NULL,              -- the bonus as it was given then
  solved_at   DATETIME    NOT NULL,              -- UTC
  PRIMARY KEY (user_id, day),
  CONSTRAINT fk_cgdl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE code_ground_submissions (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  user_id      CHAR(36)    NOT NULL,
  problem_key  VARCHAR(80) NOT NULL,
  language     VARCHAR(12) NOT NULL,
  code         MEDIUMTEXT  NOT NULL,
  verdict      VARCHAR(20) NOT NULL,             -- ACCEPTED | WRONG_ANSWER | TIME_LIMIT | RUNTIME_ERROR | COMPILE_ERROR
  passed_count INT         NOT NULL,
  total_count  INT         NOT NULL,
  created_at   DATETIME    NOT NULL,             -- UTC
  PRIMARY KEY (id),
  KEY idx_cgs_user_problem (user_id, problem_key, id),
  CONSTRAINT fk_cgs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
