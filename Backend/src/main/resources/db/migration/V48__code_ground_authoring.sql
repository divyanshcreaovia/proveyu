-- Code ground content managed by a platform admin, on top of the problems shipped with the application (resources/codeground).
CREATE TABLE code_ground_topics (
  topic_key  VARCHAR(60)  NOT NULL,
  title      VARCHAR(120) NOT NULL,
  summary    VARCHAR(300) NOT NULL,
  guide      MEDIUMTEXT   NOT NULL,             -- JSON array of slides (may be empty)
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (topic_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE code_ground_problems (
  problem_key    VARCHAR(60) NOT NULL,
  topic_key      VARCHAR(60) NOT NULL,          -- a built-in topic or one of code_ground_topics
  spec           MEDIUMTEXT  NOT NULL,          -- JSON: the latest saved version
  published_spec MEDIUMTEXT  NULL,              -- JSON: what students see; set only after the reference solution passed
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (problem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- built-in problems the admin has taken out of use
CREATE TABLE code_ground_hidden (
  problem_key VARCHAR(80) NOT NULL,
  PRIMARY KEY (problem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- the problem of each day: chosen by an admin, or picked automatically on the day
CREATE TABLE code_ground_schedule (
  day         DATE        NOT NULL,
  problem_key VARCHAR(80) NOT NULL,
  auto        TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
