-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V3__remove_proctor_and_question_bank.sql
-- ============================================================

-- 1. Remove PROCTOR from users table role column
ALTER TABLE users MODIFY COLUMN role ENUM('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'CANDIDATE';

-- 2. Drop questions and question_banks tables if existing (Exam delivery handled by desktop app)
DROP TABLE IF EXISTS exam_answers;
ALTER TABLE exam_sessions DROP FOREIGN KEY fk_session_qbank;
ALTER TABLE exam_sessions DROP COLUMN question_bank_id;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS question_banks;
