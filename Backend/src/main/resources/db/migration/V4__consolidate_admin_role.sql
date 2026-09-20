-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V4__consolidate_admin_role.sql
-- ============================================================

-- 1. Update existing SUPER_ADMIN users to ADMIN
UPDATE users SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN';

-- 2. Modify users role column to CANDIDATE, RECRUITER, ORGANIZATION, ADMIN
ALTER TABLE users MODIFY COLUMN role ENUM('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN') NOT NULL DEFAULT 'CANDIDATE';
