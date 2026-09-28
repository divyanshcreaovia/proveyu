-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V16__add_interview_rounds_to_jobs_and_invitations.sql
-- Description: Add interview_rounds and total_rounds to jobs, and current interview_round to job_invitations
-- ============================================================

-- 1. Add interview_rounds and total_rounds to jobs table
ALTER TABLE jobs
    ADD COLUMN interview_rounds TEXT NULL AFTER last_date,
    ADD COLUMN total_rounds INT DEFAULT 0 AFTER interview_rounds;

-- 2. Add interview_round to job_invitations table for stage/round tracking
ALTER TABLE job_invitations
    ADD COLUMN interview_round VARCHAR(100) NULL AFTER status;
