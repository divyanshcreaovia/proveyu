-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V14__create_jobs_and_job_invitations_tables.sql
-- Description: Create jobs and job_invitations tables for Recruiter Manage Hiring module
-- ============================================================

-- 1. Create Jobs Table
CREATE TABLE IF NOT EXISTS jobs (
    id                      CHAR(36) NOT NULL PRIMARY KEY,
    posted_by               CHAR(36) NOT NULL,
    company_id              CHAR(36) NULL,
    title                   VARCHAR(255) NOT NULL,
    description             TEXT NULL,
    role_track              VARCHAR(100) NULL,
    required_skills         TEXT NULL,
    experience_level        VARCHAR(50) NULL,
    min_experience_years    INT DEFAULT 0,
    max_experience_years    INT NULL,
    min_salary_lpa          DECIMAL(5,2) NULL,
    max_salary_lpa          DECIMAL(5,2) NULL,
    salary_package          VARCHAR(100) NULL,
    location                VARCHAR(150) NULL,
    work_mode               VARCHAR(50) NOT NULL DEFAULT 'ON_SITE',
    min_score_threshold     DOUBLE NULL DEFAULT 0.0,
    vacancies               INT DEFAULT 1,
    target_client           VARCHAR(255) NULL,
    last_date               DATE NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_jobs_posted_by FOREIGN KEY (posted_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
    INDEX idx_jobs_posted_by (posted_by),
    INDEX idx_jobs_status (status),
    INDEX idx_jobs_role_track (role_track),
    INDEX idx_jobs_last_date (last_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Create Job Invitations Table
CREATE TABLE IF NOT EXISTS job_invitations (
    id                      CHAR(36) NOT NULL PRIMARY KEY,
    recruiter_id            CHAR(36) NOT NULL,
    candidate_id            CHAR(36) NOT NULL,
    job_id                  CHAR(36) NOT NULL,
    candidate_score_id      CHAR(36) NULL,
    score_snapshot          DECIMAL(5,2) NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'INVITED',
    message                 TEXT NULL,
    notes                   TEXT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_inv_recruiter FOREIGN KEY (recruiter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_inv_candidate FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_inv_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_inv_cand_score FOREIGN KEY (candidate_score_id) REFERENCES candidate_scores(id) ON DELETE SET NULL,
    INDEX idx_job_inv_recruiter (recruiter_id),
    INDEX idx_job_inv_candidate (candidate_id),
    INDEX idx_job_inv_job (job_id),
    INDEX idx_job_inv_cand_score (candidate_score_id),
    INDEX idx_job_inv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
