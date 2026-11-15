-- =====================================================================
-- V20__migrate_legacy_placement_data.sql
-- Copies data from BOTH legacy placement models into the consolidated
-- college_* tables, then RENAMES (does not drop) the legacy tables to
-- legacy_* so nothing is lost. Drop them manually once verified.
-- On a fresh database all SELECTs are empty and this is a no-op.
-- =====================================================================

-- 0. A shared DB may have applied only ONE of the two former V17 files
--    (Flyway refused to run both). Create any missing legacy table in a
--    minimal, FK-less form so the copy statements below always succeed.
CREATE TABLE IF NOT EXISTS college_custom_roles (id CHAR(36) PRIMARY KEY, organization_id CHAR(36)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS college_team_members (id CHAR(36) PRIMARY KEY, organization_id CHAR(36), user_id CHAR(36),
    role_name VARCHAR(100), designation VARCHAR(150), assigned_branch VARCHAR(100), status VARCHAR(30)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS student_profiles (id CHAR(36) PRIMARY KEY, organization_id CHAR(36), user_id CHAR(36),
    roll_number VARCHAR(50), first_name VARCHAR(100), last_name VARCHAR(100), email VARCHAR(190), phone VARCHAR(25),
    gender VARCHAR(20), batch_year VARCHAR(20), branch VARCHAR(100), degree VARCHAR(100), tenth_percentage DOUBLE,
    twelfth_percentage DOUBLE, active_backlogs INT, gap_years INT, aggregate_cgpa DOUBLE, placement_status VARCHAR(30),
    placed_ctc VARCHAR(50), linkedin_url VARCHAR(255), github_url VARCHAR(255), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS placement_drives (id CHAR(36) PRIMARY KEY, organization_id CHAR(36), company_name VARCHAR(150),
    company_website VARCHAR(255), industry VARCHAR(100), roles VARCHAR(150), job_location VARCHAR(150), package_lpa DOUBLE,
    bond_duration_months INT, drive_mode VARCHAR(50), venue VARCHAR(150), min_cgpa DOUBLE, max_backlogs INT, max_gap_years INT,
    min_tenth_percentage DOUBLE, min_twelfth_percentage DOUBLE, status VARCHAR(50), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS placement_records (id CHAR(36) PRIMARY KEY, organization_id CHAR(36), student_profile_id CHAR(36),
    drive_id CHAR(36), company_name VARCHAR(150), role_offered VARCHAR(150), ctc VARCHAR(50), status VARCHAR(50),
    offer_date DATE, joining_date DATE) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS company (company_id CHAR(36) PRIMARY KEY, name VARCHAR(255), category VARCHAR(100), website VARCHAR(255)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS branch (branch_id CHAR(36) PRIMARY KEY, college_id CHAR(36), branch_name VARCHAR(100),
    branch_code VARCHAR(50), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS student (student_id CHAR(36) PRIMARY KEY, college_id CHAR(36), branch_id CHAR(36), roll_number VARCHAR(50),
    cgpa DECIMAL(4,2), tenth_percent DECIMAL(5,2), twelfth_percent DECIMAL(5,2), active_backlogs INT, gap_years INT,
    placed_status VARCHAR(20), highest_package_lpa DECIMAL(6,2), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS tier (tier_id CHAR(36) PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS package_tier_rule (rule_id CHAR(36) PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS placement_drive (drive_id CHAR(36) PRIMARY KEY, college_id CHAR(36), company_id CHAR(36),
    offered_package DECIMAL(6,2), drive_date DATE, location VARCHAR(255), status VARCHAR(20), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_eligibility (drive_id CHAR(36) PRIMARY KEY, min_cgpa DECIMAL(4,2), min_10th_percent DECIMAL(5,2),
    min_12th_percent DECIMAL(5,2), max_active_backlogs INT, max_gap_years INT) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_tier (drive_id CHAR(36), tier_id CHAR(36)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_branch (drive_id CHAR(36), branch_id CHAR(36)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_role (drive_role_id CHAR(36) PRIMARY KEY, drive_id CHAR(36), role_name VARCHAR(150)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS hiring_round (round_id CHAR(36) PRIMARY KEY, round_name VARCHAR(100)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_round (drive_round_id CHAR(36) PRIMARY KEY, drive_id CHAR(36), round_id CHAR(36), round_number INT,
    round_date DATE, status VARCHAR(20), created_at DATETIME) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS drive_round_student (id CHAR(36) PRIMARY KEY, drive_round_id CHAR(36), student_id CHAR(36),
    status VARCHAR(20), score DECIMAL(6,2), remarks VARCHAR(500)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS placement_offer (offer_id CHAR(36) PRIMARY KEY, drive_id CHAR(36), student_id CHAR(36), role_name VARCHAR(150),
    package_lpa DECIMAL(6,2), offer_status VARCHAR(20), offered_at DATETIME) ENGINE=InnoDB;

-- 1. Every COLLEGE/UNIVERSITY organization becomes an approved college profile
INSERT IGNORE INTO college_profiles (college_id, college_name, tpo_email, tpo_phone, approval_status)
SELECT o.id, o.legal_name, o.contact_email, o.contact_phone, 'APPROVED'
FROM organizations o
WHERE o.org_type IN ('COLLEGE','UNIVERSITY');

-- 2. Organization owners -> COLLEGE_ADMIN members
INSERT IGNORE INTO college_members (id, college_id, user_id, role, status)
SELECT UUID(), o.id, o.user_id, 'COLLEGE_ADMIN', 'ACTIVE'
FROM organizations o
WHERE o.org_type IN ('COLLEGE','UNIVERSITY');

UPDATE users u JOIN college_members m ON m.user_id = u.id AND m.role = 'COLLEGE_ADMIN'
SET u.role = 'COLLEGE_ADMIN'
WHERE u.role = 'ORGANIZATION';

-- 3. Branches: legacy normalized `branch` keeps its ids; free-text branches from student_profiles get created
INSERT IGNORE INTO college_branches (id, college_id, code, name, created_at)
SELECT b.branch_id, b.college_id, b.branch_code, b.branch_name, b.created_at FROM branch b;

INSERT IGNORE INTO college_branches (id, college_id, code, name)
SELECT UUID(), x.organization_id, LEFT(UPPER(REPLACE(REPLACE(x.branch,' ',''),'&','')), 30), x.branch
FROM (SELECT DISTINCT organization_id, branch FROM student_profiles) x;

-- 4. Legacy team members (model B) -> members (non-candidate users only)
INSERT IGNORE INTO college_members (id, college_id, user_id, role, designation, status, branch_scope)
SELECT UUID(), t.organization_id, t.user_id,
       CASE WHEN LOWER(t.role_name) LIKE '%admin%' OR LOWER(t.role_name) LIKE '%tpo%' THEN 'COLLEGE_ADMIN' ELSE 'COLLEGE_COORDINATOR' END,
       t.designation,
       CASE WHEN t.status = 'INACTIVE' THEN 'REVOKED' ELSE 'ACTIVE' END,
       (SELECT cb.id FROM college_branches cb WHERE cb.college_id = t.organization_id AND cb.name = t.assigned_branch LIMIT 1)
FROM college_team_members t
JOIN users u ON u.id = t.user_id
WHERE u.role IN ('ORGANIZATION','RECRUITER','ADMIN');

UPDATE users u JOIN college_members m ON m.user_id = u.id
SET u.role = m.role
WHERE u.role = 'ORGANIZATION';

-- 5. Students: model B (student_profiles) first, it is the richer record
INSERT IGNORE INTO college_students (id, college_id, user_id, roll_number, first_name, last_name, email, phone, gender,
    branch_id, batch_year, degree, cgpa, tenth_percent, twelfth_percent, active_backlogs, gap_years,
    linkedin_url, github_url, status, highest_ctc_lpa, created_at)
SELECT sp.id, sp.organization_id, sp.user_id, sp.roll_number, sp.first_name, sp.last_name, sp.email, sp.phone, sp.gender,
       (SELECT cb.id FROM college_branches cb WHERE cb.college_id = sp.organization_id AND cb.name = sp.branch LIMIT 1),
       COALESCE(CAST(NULLIF(REGEXP_REPLACE(sp.batch_year, '[^0-9]', ''), '') AS UNSIGNED), YEAR(CURDATE())),
       sp.degree, LEAST(sp.aggregate_cgpa, 10), sp.tenth_percentage, sp.twelfth_percentage, sp.active_backlogs, sp.gap_years,
       sp.linkedin_url, sp.github_url,
       CASE WHEN LOWER(sp.placement_status) = 'placed' THEN 'PLACED' ELSE 'ACTIVE' END,
       ROUND(NULLIF(REGEXP_REPLACE(sp.placed_ctc, '[^0-9.]', ''), '') + 0, 2),
       sp.created_at
FROM student_profiles sp;

-- then model A (`student`, keyed by users.id); duplicates are skipped by the unique keys
INSERT IGNORE INTO college_students (id, college_id, user_id, roll_number, first_name, email, phone, branch_id, batch_year,
    cgpa, tenth_percent, twelfth_percent, active_backlogs, gap_years, status, highest_ctc_lpa, created_at)
SELECT UUID(), s.college_id, s.student_id, COALESCE(s.roll_number, CONCAT('LEGACY-', LEFT(s.student_id, 8))),
       u.full_name, u.email, u.phone, s.branch_id, YEAR(CURDATE()),
       s.cgpa, s.tenth_percent, s.twelfth_percent, s.active_backlogs, s.gap_years,
       CASE WHEN s.placed_status = 'PLACED' THEN 'PLACED' ELSE 'ACTIVE' END, s.highest_package_lpa, s.created_at
FROM student s JOIN users u ON u.id = s.student_id
WHERE NOT EXISTS (SELECT 1 FROM college_students cs WHERE cs.college_id = s.college_id AND cs.user_id = s.student_id);

-- 6. Companies (legacy model A companies are global; scope them to each college that used them)
INSERT IGNORE INTO college_companies (id, college_id, name, industry, website)
SELECT UUID(), x.college_id, x.name, x.category, x.website
FROM (SELECT DISTINCT d.college_id, c.name, c.category, c.website
      FROM placement_drive d JOIN company c ON c.company_id = d.company_id) x;

INSERT IGNORE INTO college_companies (id, college_id, name, industry, website)
SELECT UUID(), x.organization_id, x.company_name, x.industry, x.company_website
FROM (SELECT organization_id, company_name, MAX(industry) industry, MAX(company_website) company_website
      FROM placement_drives GROUP BY organization_id, company_name) x;

-- 7. Drives
INSERT IGNORE INTO college_drives (id, college_id, company_id, title, role_title, ctc_lpa, location, drive_date, status,
    min_cgpa, min_tenth, min_twelfth, max_active_backlogs, max_gap_years, created_at)
SELECT d.drive_id, d.college_id, cc.id, CONCAT(c.name, ' Campus Drive'),
       COALESCE((SELECT r.role_name FROM drive_role r WHERE r.drive_id = d.drive_id LIMIT 1), 'Graduate Engineer'),
       d.offered_package, d.location, d.drive_date,
       CASE d.status WHEN 'LISTED' THEN 'PUBLISHED' ELSE d.status END,
       e.min_cgpa, e.min_10th_percent, e.min_12th_percent, e.max_active_backlogs, e.max_gap_years, d.created_at
FROM placement_drive d
JOIN company c ON c.company_id = d.company_id
JOIN college_companies cc ON cc.college_id = d.college_id AND cc.name = c.name
LEFT JOIN drive_eligibility e ON e.drive_id = d.drive_id;

UPDATE college_drives cd
SET cd.branch_ids = (SELECT GROUP_CONCAT(db.branch_id) FROM drive_branch db WHERE db.drive_id = cd.id)
WHERE EXISTS (SELECT 1 FROM drive_branch db WHERE db.drive_id = cd.id);

INSERT IGNORE INTO college_drives (id, college_id, company_id, title, role_title, ctc_lpa, location, bond_months,
    venue, mode, status, min_cgpa, min_tenth, min_twelfth, max_active_backlogs, max_gap_years, created_at)
SELECT p.id, p.organization_id, cc.id, CONCAT(p.company_name, ' Drive'), p.roles, p.package_lpa, p.job_location,
       COALESCE(p.bond_duration_months, 0), p.venue,
       CASE WHEN LOWER(p.drive_mode) LIKE '%online%' OR LOWER(p.drive_mode) LIKE '%virtual%' THEN 'ONLINE' ELSE 'OFFLINE' END,
       CASE WHEN LOWER(p.status) LIKE '%complete%' THEN 'COMPLETED'
            WHEN LOWER(p.status) LIKE '%cancel%' THEN 'CANCELLED'
            ELSE 'PUBLISHED' END,
       NULLIF(p.min_cgpa, 0), NULLIF(p.min_tenth_percentage, 0), NULLIF(p.min_twelfth_percentage, 0),
       p.max_backlogs, p.max_gap_years, p.created_at
FROM placement_drives p
JOIN college_companies cc ON cc.college_id = p.organization_id AND cc.name = p.company_name;

-- 8. Rounds, applications and results (model A pipeline)
INSERT IGNORE INTO college_drive_rounds (id, college_id, drive_id, seq, name, round_type, scheduled_at, status, created_at)
SELECT dr.drive_round_id, cd.college_id, dr.drive_id, dr.round_number, hr.round_name,
       CASE WHEN hr.round_name LIKE '%Coding%' THEN 'CODING'
            WHEN hr.round_name LIKE '%Online%' THEN 'APTITUDE'
            WHEN hr.round_name LIKE '%Group%' THEN 'GD'
            WHEN hr.round_name LIKE '%Technical%' THEN 'TECHNICAL'
            WHEN hr.round_name LIKE '%HR%' THEN 'HR' ELSE 'CUSTOM' END,
       dr.round_date, dr.status, dr.created_at
FROM drive_round dr
JOIN college_drives cd ON cd.id = dr.drive_id
JOIN hiring_round hr ON hr.round_id = dr.round_id;

INSERT IGNORE INTO college_applications (id, college_id, drive_id, student_id, status, source, current_round)
SELECT UUID(), x.college_id, x.drive_id, x.student_id, 'IN_PROCESS', 'TPO', x.max_round
FROM (SELECT cd.college_id, dr.drive_id, cs.id AS student_id, MAX(dr.round_number) AS max_round
      FROM drive_round_student drs
      JOIN drive_round dr ON dr.drive_round_id = drs.drive_round_id
      JOIN college_drives cd ON cd.id = dr.drive_id
      JOIN college_students cs ON cs.user_id = drs.student_id AND cs.college_id = cd.college_id
      GROUP BY cd.college_id, dr.drive_id, cs.id) x;

INSERT IGNORE INTO college_round_results (id, college_id, round_id, application_id, result, score, remarks)
SELECT UUID(), ca.college_id, drs.drive_round_id, ca.id,
       CASE drs.status WHEN 'SELECTED' THEN 'PASS' WHEN 'REJECTED' THEN 'FAIL' WHEN 'ABSENT' THEN 'ABSENT' ELSE 'PENDING' END,
       drs.score, drs.remarks
FROM drive_round_student drs
JOIN drive_round dr ON dr.drive_round_id = drs.drive_round_id
JOIN college_drives cd ON cd.id = dr.drive_id
JOIN college_students cs ON cs.user_id = drs.student_id AND cs.college_id = cd.college_id
JOIN college_applications ca ON ca.drive_id = dr.drive_id AND ca.student_id = cs.id;

-- 9. Offers
INSERT IGNORE INTO college_offers (id, college_id, student_id, drive_id, company_id, role_title, ctc_lpa, status, offer_date)
SELECT po.offer_id, cd.college_id, cs.id, po.drive_id, cd.company_id, COALESCE(po.role_name, cd.role_title), po.package_lpa,
       CASE po.offer_status WHEN 'REJECTED' THEN 'DECLINED' ELSE po.offer_status END, DATE(po.offered_at)
FROM placement_offer po
JOIN college_drives cd ON cd.id = po.drive_id
JOIN college_students cs ON cs.user_id = po.student_id AND cs.college_id = cd.college_id;

INSERT IGNORE INTO college_offers (id, college_id, student_id, drive_id, company_id, role_title, ctc_lpa, status, offer_date, joining_date)
SELECT pr.id, pr.organization_id, pr.student_profile_id, pr.drive_id,
       COALESCE((SELECT cd.company_id FROM college_drives cd WHERE cd.id = pr.drive_id),
                (SELECT cc.id FROM college_companies cc WHERE cc.college_id = pr.organization_id AND cc.name = pr.company_name LIMIT 1)),
       pr.role_offered, COALESCE(ROUND(NULLIF(REGEXP_REPLACE(pr.ctc, '[^0-9.]', ''), '') + 0, 2), 0),
       CASE UPPER(pr.status) WHEN 'ACCEPTED' THEN 'ACCEPTED' WHEN 'JOINED' THEN 'JOINED' WHEN 'DECLINED' THEN 'DECLINED'
                             WHEN 'REVOKED' THEN 'REVOKED' ELSE 'OFFERED' END,
       pr.offer_date, pr.joining_date
FROM placement_records pr
WHERE EXISTS (SELECT 1 FROM college_students cs WHERE cs.id = pr.student_profile_id)
  AND (pr.drive_id IS NOT NULL OR EXISTS (SELECT 1 FROM college_companies cc WHERE cc.college_id = pr.organization_id AND cc.name = pr.company_name));

-- 10. Rename legacy tables (kept for verification; drop manually later)
RENAME TABLE
    placement_records          TO legacy_placement_records,
    placement_drives           TO legacy_placement_drives,
    student_profiles           TO legacy_student_profiles,
    college_team_members       TO legacy_college_team_members,
    college_custom_roles       TO legacy_college_custom_roles,
    placement_offer            TO legacy_placement_offer,
    drive_round_student        TO legacy_drive_round_student,
    drive_round                TO legacy_drive_round,
    hiring_round               TO legacy_hiring_round,
    drive_role                 TO legacy_drive_role,
    drive_branch               TO legacy_drive_branch,
    drive_tier                 TO legacy_drive_tier,
    drive_eligibility          TO legacy_drive_eligibility,
    placement_drive            TO legacy_placement_drive,
    package_tier_rule          TO legacy_package_tier_rule,
    tier                       TO legacy_tier,
    student                    TO legacy_student,
    branch                     TO legacy_branch,
    company                    TO legacy_company;
