-- V24: AI mock interview (Preparation > AI interview). Runs on self-hosted models: Ollama (questions, follow-ups,
-- scoring), Whisper.cpp (speech to text), Piper (text to speech). Students are the existing college_students rows,
-- so results can later feed the TPO dashboard and the ProveYu Tested Tier. Audio lives in file storage, not here.

CREATE TABLE IF NOT EXISTS ai_interview_sessions (
    id              CHAR(36)     NOT NULL,
    college_id      CHAR(36)     NOT NULL,
    student_id      CHAR(36)     NOT NULL,
    user_id         CHAR(36)     NOT NULL,
    drive_id        CHAR(36)     NULL,                      -- "practise for this drive": questions come from its job description
    interview_type  ENUM('TECHNICAL','HR','MIXED') NOT NULL,
    role_title      VARCHAR(200) NOT NULL,
    status          ENUM('IN_PROGRESS','EVALUATING','COMPLETED','FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
    planned_questions INT        NOT NULL,                  -- main questions (follow-ups come on top)
    credit_source   ENUM('FREE','PAID') NOT NULL,
    consent_at      DATETIME     NOT NULL,                  -- student agreed to recording and AI evaluation (DPDP)
    started_at      DATETIME     NOT NULL,
    ended_at        DATETIME     NULL,
    error_message   VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_aisess_user (user_id, started_at),
    INDEX idx_aisess_college (college_id),
    INDEX idx_aisess_status (status),
    CONSTRAINT fk_aisess_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_aisess_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE,
    CONSTRAINT fk_aisess_drive FOREIGN KEY (drive_id) REFERENCES college_drives(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_interview_questions (
    id              CHAR(36)     NOT NULL,
    session_id      CHAR(36)     NOT NULL,
    seq             INT          NOT NULL,                  -- order asked, follow-ups included
    question_text   VARCHAR(1000) NOT NULL,
    topic           VARCHAR(120) NULL,
    parent_question_id CHAR(36)  NULL,                      -- set for a follow-up
    asked_at        DATETIME     NULL,                      -- null = planned, not asked yet
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_aiq_session_seq (session_id, seq),
    CONSTRAINT fk_aiq_session FOREIGN KEY (session_id) REFERENCES ai_interview_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_interview_answers (
    id              CHAR(36)     NOT NULL,
    session_id      CHAR(36)     NOT NULL,
    question_id     CHAR(36)     NOT NULL,
    transcript      TEXT         NOT NULL,
    input_mode      ENUM('VOICE','TEXT') NOT NULL,
    audio_path      VARCHAR(300) NULL,                      -- relative path in interview file storage; deleted after retention
    duration_seconds INT         NULL,
    words_per_minute INT         NULL,
    answered_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_aians_question (question_id),
    INDEX idx_aians_session (session_id),
    CONSTRAINT fk_aians_session FOREIGN KEY (session_id) REFERENCES ai_interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_aians_question FOREIGN KEY (question_id) REFERENCES ai_interview_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_answer_evaluations (
    id              CHAR(36)     NOT NULL,
    answer_id       CHAR(36)     NOT NULL,
    content         TINYINT      NOT NULL,                  -- 1..5
    communication   TINYINT      NOT NULL,
    confidence      TINYINT      NOT NULL,
    relevance       TINYINT      NOT NULL,
    feedback        VARCHAR(1000) NULL,
    better_answer   VARCHAR(1500) NULL,                     -- short model answer / what was missing
    model           VARCHAR(80)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_aieval_answer (answer_id),
    CONSTRAINT fk_aieval_answer FOREIGN KEY (answer_id) REFERENCES ai_interview_answers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_interview_reports (
    id              CHAR(36)     NOT NULL,
    session_id      CHAR(36)     NOT NULL,
    overall_score   DECIMAL(5,2) NOT NULL,                  -- 0..100
    recommendation  ENUM('READY','ALMOST_READY','NEEDS_PRACTICE') NOT NULL,
    summary         VARCHAR(2000) NULL,
    strengths_json  TEXT         NULL,
    improvements_json TEXT       NULL,
    model           VARCHAR(80)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_airep_session (session_id),
    CONSTRAINT fk_airep_session FOREIGN KEY (session_id) REFERENCES ai_interview_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- First interview free for every student, then paid credits (bought by the student or given in bulk by the college).
CREATE TABLE IF NOT EXISTS interview_credits (
    student_id      CHAR(36)     NOT NULL,
    college_id      CHAR(36)     NOT NULL,
    free_used       INT          NOT NULL DEFAULT 0,
    paid_balance    INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,        -- optimistic lock: two tabs cannot spend the same credit
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    INDEX idx_icred_college (college_id),
    CONSTRAINT fk_icred_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
