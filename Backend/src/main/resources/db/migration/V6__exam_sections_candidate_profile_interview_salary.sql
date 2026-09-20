-- Create Exam Sections Table
CREATE TABLE IF NOT EXISTS exam_sections (
    id CHAR(36) NOT NULL PRIMARY KEY,
    exam_id CHAR(36) NOT NULL,
    skill_id CHAR(36) NOT NULL,
    section_name VARCHAR(100) NOT NULL,
    duration_minutes INT NOT NULL,
    total_marks INT NOT NULL,
    passing_marks INT NOT NULL,
    CONSTRAINT fk_exam_sec_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_sec_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Recreate / Alter candidate_profiles to add UAN, fresher college info, verified experience months
DROP TABLE IF EXISTS candidate_preferred_locations;
DROP TABLE IF EXISTS candidate_profiles;

CREATE TABLE candidate_profiles (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    experience_track VARCHAR(20) NOT NULL DEFAULT 'FRESHER',
    years_of_experience INT DEFAULT 0,
    uan_number VARCHAR(50) NULL,
    verified_backend_experience_months INT DEFAULT 0,
    college_name VARCHAR(150) NULL,
    degree_branch VARCHAR(100) NULL,
    passout_year INT NULL,
    skills_list VARCHAR(255) NULL,
    resume_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cand_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create Candidate Scores Table
CREATE TABLE IF NOT EXISTS candidate_scores (
    id CHAR(36) NOT NULL PRIMARY KEY,
    candidate_id CHAR(36) NOT NULL,
    exam_id CHAR(36) NOT NULL,
    skill_id CHAR(36) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    max_score DECIMAL(5,2) NOT NULL,
    passed TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cand_score_user FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cand_score_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_cand_score_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Update Interview Invites Table for Salary / Package Offered
ALTER TABLE interview_invites
ADD COLUMN min_salary_lpa DECIMAL(5,2) NULL,
ADD COLUMN max_salary_lpa DECIMAL(5,2) NULL,
ADD COLUMN package_range VARCHAR(100) NULL;
