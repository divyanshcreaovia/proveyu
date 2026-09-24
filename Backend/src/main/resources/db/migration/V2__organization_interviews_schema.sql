-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V2__organization_interviews_schema.sql
-- ============================================================

-- 1. Modify users role column to support ORGANIZATION and SUPER_ADMIN
ALTER TABLE users MODIFY COLUMN role ENUM('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN','SUPER_ADMIN','PROCTOR') NOT NULL DEFAULT 'CANDIDATE';

-- 2. Organizations Table (Colleges, Universities, Training Institutes, Consultancies, Companies)
CREATE TABLE IF NOT EXISTS organizations (
    id              CHAR(36) PRIMARY KEY,
    user_id         CHAR(36) NOT NULL,
    legal_name      VARCHAR(200) NOT NULL,
    org_type        ENUM('COLLEGE','UNIVERSITY','TRAINING_INSTITUTE','CONSULTANCY','COMPANY') NOT NULL DEFAULT 'COLLEGE',
    code_or_gstin   VARCHAR(50) NULL,
    contact_email   VARCHAR(190) NULL,
    contact_phone   VARCHAR(20) NULL,
    address         TEXT NULL,
    is_verified     TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_org_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_org_type (org_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Organization Candidates (Students/Candidates registered under an Organization)
CREATE TABLE IF NOT EXISTS organization_candidates (
    id                CHAR(36) PRIMARY KEY,
    organization_id   CHAR(36) NOT NULL,
    candidate_id      CHAR(36) NOT NULL,
    roll_number_or_id VARCHAR(50) NULL,
    batch_year        VARCHAR(20) NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_org_candidate (organization_id, candidate_id),
    CONSTRAINT fk_org_cand_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_cand_candidate FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_org_cand_candidate (candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Interview Invites & Placement Tracking
CREATE TABLE IF NOT EXISTS interview_invites (
    id                CHAR(36) PRIMARY KEY,
    recruiter_id      CHAR(36) NOT NULL,
    company_id        CHAR(36) NULL,
    candidate_id      CHAR(36) NOT NULL,
    exam_id           CHAR(36) NULL,
    job_title         VARCHAR(150) NOT NULL,
    job_description   TEXT NULL,
    status            ENUM('PENDING','ACCEPTED','REJECTED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    placement_status  ENUM('IN_PROCESS','PLACED','NOT_PLACED') NOT NULL DEFAULT 'IN_PROCESS',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invite_recruiter FOREIGN KEY (recruiter_id) REFERENCES users(id),
    CONSTRAINT fk_invite_candidate FOREIGN KEY (candidate_id) REFERENCES users(id),
    CONSTRAINT fk_invite_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_invite_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    INDEX idx_invite_candidate (candidate_id),
    INDEX idx_invite_recruiter (recruiter_id),
    INDEX idx_invite_status (status, placement_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
