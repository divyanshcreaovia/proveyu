-- AI interview: a student can paste a job description for a general role, and the questions are limited to its technologies.
ALTER TABLE ai_interview_sessions
    ADD COLUMN job_description TEXT NULL AFTER planned_questions,
    ADD COLUMN focus_skills VARCHAR(500) NULL AFTER job_description;
