-- College exams (sessionals) on top of the college's own mock tests. A college test is PRACTICE (as before: any time, retakes,
-- answers shown) or EXAM: one attempt, a fixed time window, only the chosen branches / batch years, optional question pool and
-- shuffling, answers and results hidden until the college releases them, descriptive (TEXT) questions marked by a teacher.
-- Existing tests stay PRACTICE and behave exactly as before.
ALTER TABLE prep_tests ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'PRACTICE' AFTER published;
ALTER TABLE prep_tests ADD COLUMN starts_at DATETIME NULL AFTER mode;
ALTER TABLE prep_tests ADD COLUMN ends_at DATETIME NULL AFTER starts_at;
ALTER TABLE prep_tests ADD COLUMN branch_ids TEXT NULL AFTER ends_at;                -- CSV of college_branches.id; NULL = every branch
ALTER TABLE prep_tests ADD COLUMN batch_years VARCHAR(100) NULL AFTER branch_ids;     -- CSV of years; NULL = every batch
ALTER TABLE prep_tests ADD COLUMN shuffle_questions TINYINT(1) NOT NULL DEFAULT 0 AFTER batch_years;
ALTER TABLE prep_tests ADD COLUMN shuffle_options TINYINT(1) NOT NULL DEFAULT 0 AFTER shuffle_questions;
ALTER TABLE prep_tests ADD COLUMN show_answers TINYINT(1) NOT NULL DEFAULT 1 AFTER shuffle_options;       -- correct answers on the result page
ALTER TABLE prep_tests ADD COLUMN results_released TINYINT(1) NOT NULL DEFAULT 1 AFTER show_answers;     -- students can see their score

-- Per attempt: which questions (pool) in which order, the option order of each MCQ (JSON; NULL = all questions, original order),
-- minutes added by an invigilator, and the review state of descriptive answers (NULL = nothing to mark, PENDING, DONE).
ALTER TABLE prep_attempts ADD COLUMN layout_json TEXT NULL AFTER answers_json;
ALTER TABLE prep_attempts ADD COLUMN extra_minutes INT NOT NULL DEFAULT 0 AFTER ai_warning_count;
ALTER TABLE prep_attempts ADD COLUMN review_status VARCHAR(10) NULL AFTER extra_minutes;

CREATE INDEX idx_prep_tests_college_mode ON prep_tests (college_id, mode, published);
CREATE INDEX idx_prep_attempts_test ON prep_attempts (test_id, user_id);
