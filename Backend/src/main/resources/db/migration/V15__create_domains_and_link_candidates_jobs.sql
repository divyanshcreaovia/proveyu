-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V15__create_domains_and_link_candidates_jobs.sql
-- Description: Create domains table and link to candidate_profiles and jobs
-- ============================================================

-- 1. Create Domains Table
CREATE TABLE IF NOT EXISTS domains (
    id                      CHAR(36) NOT NULL PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL UNIQUE,
    code                    VARCHAR(100) NOT NULL UNIQUE,
    description             TEXT NULL,
    active                  TINYINT(1) NOT NULL DEFAULT 1,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_domains_code (code),
    INDEX idx_domains_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Seed Standard Domains
INSERT INTO domains (id, name, code, description, active) VALUES
('d0000000-0000-0000-0000-000000000001', 'QA & Testing', 'qa-testing', 'Automation, Performance, Selenium, Cypress, Playwright, Manual Testing & Quality Engineering', 1),
('d0000000-0000-0000-0000-000000000002', 'Java Spring Boot Microservices', 'java-spring-boot', 'Core Java, Spring Boot, Microservices, Hibernate, REST APIs, System Design', 1),
('d0000000-0000-0000-0000-000000000003', 'Full Stack Engineering', 'full-stack', 'End-to-end web architecture, Node.js, React, Angular, Databases, Cloud Deployments', 1),
('d0000000-0000-0000-0000-000000000004', 'Frontend React / Angular', 'frontend-react-angular', 'Modern web UI development, React, Angular, TypeScript, State Management, UI/UX', 1),
('d0000000-0000-0000-0000-000000000005', 'DevOps & Cloud', 'devops-cloud', 'CI/CD Pipelines, Docker, Kubernetes, AWS/Azure Cloud Infrastructure, Terraform, Monitoring', 1),
('d0000000-0000-0000-0000-000000000006', 'Python Backend & Data', 'python-backend-data', 'Python, FastAPI, Django, Data Engineering, Pandas, Machine Learning & Analytics', 1),
('d0000000-0000-0000-0000-000000000007', 'Universal Competency Passport', 'universal-competency', 'Fundamental programming, Data Structures, Algorithms, Problem Solving & Database Design', 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description);

-- 3. Add domain_id to candidate_profiles
ALTER TABLE candidate_profiles
    ADD COLUMN domain_id CHAR(36) NULL AFTER user_id,
    ADD CONSTRAINT fk_candidate_profile_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE SET NULL;

CREATE INDEX idx_candidate_profiles_domain ON candidate_profiles(domain_id);

-- 4. Add domain_id to jobs
ALTER TABLE jobs
    ADD COLUMN domain_id CHAR(36) NULL AFTER company_id,
    ADD CONSTRAINT fk_jobs_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE SET NULL;

CREATE INDEX idx_jobs_domain ON jobs(domain_id);

-- 5. Backfill domain_id on existing jobs
UPDATE jobs j
JOIN domains d ON (
    j.role_track = d.name
    OR (j.role_track LIKE '%QA%' AND d.code = 'qa-testing')
    OR (j.role_track LIKE '%Java%' AND d.code = 'java-spring-boot')
    OR (j.role_track LIKE '%Backend%' AND d.code = 'java-spring-boot' AND j.title LIKE '%Java%')
    OR (j.role_track LIKE '%Python%' AND d.code = 'python-backend-data')
    OR (j.role_track LIKE '%Full Stack%' AND d.code = 'full-stack')
    OR (j.role_track LIKE '%Frontend%' AND d.code = 'frontend-react-angular')
    OR (j.role_track LIKE '%DevOps%' AND d.code = 'devops-cloud')
)
SET j.domain_id = d.id
WHERE j.domain_id IS NULL;

-- 6. Backfill domain_id on existing candidate profiles
UPDATE candidate_profiles cp
SET cp.domain_id = 'd0000000-0000-0000-0000-000000000002'
WHERE cp.domain_id IS NULL AND (cp.skills_list LIKE '%Java%' OR cp.skills_list LIKE '%Spring%');

UPDATE candidate_profiles cp
SET cp.domain_id = 'd0000000-0000-0000-0000-000000000006'
WHERE cp.domain_id IS NULL AND cp.skills_list LIKE '%Python%';

UPDATE candidate_profiles cp
SET cp.domain_id = 'd0000000-0000-0000-0000-000000000001'
WHERE cp.domain_id IS NULL AND (cp.skills_list LIKE '%QA%' OR cp.skills_list LIKE '%Test%');

UPDATE candidate_profiles cp
SET cp.domain_id = 'd0000000-0000-0000-0000-000000000007'
WHERE cp.domain_id IS NULL;

-- 7. Seed verified profiles for existing candidates without profiles
INSERT INTO candidate_profiles (id, user_id, domain_id, experience_track, years_of_experience, college_name, degree_branch, passout_year, skills_list, location, headline)
VALUES
('7352c63c-624d-431c-b045-228a0e845f23', '0ce31fa4-8c75-4e6f-9369-19fc0525316c', 'd0000000-0000-0000-0000-000000000006', 'FRESHER', 0, 'IIT Kanpur', 'Computer Science', 2025, 'Python, FastAPI, Pandas, Data Structures, PostgreSQL', 'Bengaluru, KA', 'Python Backend & Data Specialist'),
('c54b7323-0f1e-46f2-aff8-f0da2631b86e', '8d3d574a-d9ee-4ba0-a476-89583ffeb05e', 'd0000000-0000-0000-0000-000000000002', 'EXPERIENCED', 2, 'BMS College of Engineering', 'Information Science', 2023, 'Java, Spring Boot, MySQL, Microservices, Hibernate', 'Bengaluru, KA', 'Java Spring Boot Microservices Engineer'),
('a1111111-2222-3333-4444-555555555501', '355a989a-77ff-4399-9644-e7bf42aba511', 'd0000000-0000-0000-0000-000000000001', 'EXPERIENCED', 2, 'PES University', 'Computer Science', 2023, 'Java, Selenium, JUnit, TestNG, Cypress, Automation Testing', 'Hyderabad, TS', 'QA & Automation Testing Lead'),
('a1111111-2222-3333-4444-555555555502', '2d5cfbae-879a-4b27-b498-614a8a02c810', 'd0000000-0000-0000-0000-000000000001', 'FRESHER', 1, 'COEP Pune', 'Information Technology', 2024, 'Selenium, Appium, Playwright, API Testing, JIRA, Postman', 'Pune, MH', 'QA Automation & Performance Tester'),
('a1111111-2222-3333-4444-555555555503', '5647032a-a08f-4328-bb9f-bb63295af6bd', 'd0000000-0000-0000-0000-000000000003', 'FRESHER', 0, 'MS Ramaiah Inst. of Technology', 'Computer Science', 2025, 'Node.js, React, Express, MongoDB, TypeScript, Next.js', 'Bengaluru, KA', 'Full Stack Software Engineer'),
('a1111111-2222-3333-4444-555555555504', '7a914264-53dc-4986-94ac-c9566c713952', 'd0000000-0000-0000-0000-000000000007', 'FRESHER', 0, 'IIT Bombay', 'Computer Science & Engineering', 2026, 'Data Structures, Algorithms, C++, SQL, System Design', 'Mumbai, MH', 'Universal Competency Passport Holder')
ON DUPLICATE KEY UPDATE domain_id=VALUES(domain_id), skills_list=VALUES(skills_list), headline=VALUES(headline);
