-- ============================================================
-- PROVEYU Database Schema Optimization
-- Migration: V7__clean_unused_tables_and_fields.sql
-- Drop any legacy unused tables and ensure clean indexes
-- ============================================================

-- Ensure unused legacy tables are safely dropped
DROP TABLE IF EXISTS candidate_preferred_locations;

-- Optimize indexes for Candidate Profiles and Interview Invites
ALTER TABLE candidate_profiles ADD INDEX idx_cand_profile_track (experience_track);
ALTER TABLE interview_invites ADD INDEX idx_interview_status (status);
ALTER TABLE interview_invites ADD INDEX idx_interview_cand (candidate_id);
ALTER TABLE interview_invites ADD INDEX idx_interview_recruiter (recruiter_id);
