# PROVEYU Database Schema Documentation

This document provides a comprehensive, production-grade reference for all database tables in the **PROVEYU** MySQL database (`PROVEYU`).

---

## Table of Contents
1. [Overview](#1-overview)
2. [Detailed Table Specifications](#2-detailed-table-specifications)
   * **Core Identity & Authentication**
     * [1. `users`](#1-users)
   * **Candidate Profiles, Search & Preferences**
     * [2. `candidate_profiles`](#2-candidate_profiles)
     * [3. `candidate_search_index` (Specialized Indexing Table)](#3-candidate_search_index-specialized-indexing-table)
     * [4. `candidate_settings` (Settings & Alert Preferences)](#4-candidate_settings)
   * **Recruiter & Corporate Verification**
     * [5. `companies`](#5-companies)
     * [6. `recruiter_profiles`](#6-recruiter_profiles)
   * **Institution / Organization Base**
     * [7. `organization_profiles`](#7-organization_profiles)
     * [8. `organization_candidates`](#8-organization_candidates)
   * **Assessment & Examination Engine**
     * [9. `skills`](#9-skills)
     * [10. `exams`](#10-exams)
     * [11. `exam_sections`](#11-exam_sections)
     * [12. `candidate_scores`](#12-candidate_scores)
   * **Test Centers & Concurrency Slot Bookings**
     * [13. `test_centers`](#13-test_centers)
     * [14. `rooms`](#14-rooms)
     * [15. `date_slots`](#15-date_slots)
     * [16. `bookings`](#16-bookings)
   * **Recruitment, Real-Time Chat & Communications**
     * [17. `interview_invites`](#17-interview_invites)
     * [18. `chat_messages` (Real-Time Messaging & Attachments)](#18-chat_messages)
     * [19. `notifications` (User In-App Notifications)](#19-notifications)
   * **Database Migrations**
     * [20. `flyway_schema_history`](#20-flyway_schema_history)
3. [Placement Management Subsystem (Campus Recruitment & Dynamic Custom RBAC)](#3-placement-management-subsystem-campus-recruitment--custom-rbac)
   * [21. `college_custom_roles`](#21-college_custom_roles)
   * [22. `college_team_members`](#22-college_team_members)
   * [23. `student_profiles`](#23-student_profiles)
   * [24. `placement_drives`](#24-placement_drives)
   * [25. `placement_records`](#25-placement_records)

---

## 1. Overview
The PROVEYU database is designed around transactional integrity, high-concurrency slot reservations, candidate assessment transparency, and recruiter talent discovery.

Key design principles:
- **UUID Primary Keys**: High uniqueness and security across distributed components (`CHAR(36)` / `VARCHAR(36)`).
- **Auditing Columns**: `created_at` and `updated_at` timestamps on all business entities.
- **Read-Optimized Projections**: `candidate_search_index` isolates heavy candidate search queries from candidate profile updates.
- **Dynamic Institutional RBAC**: Eliminates hardcoded role enums in favor of dynamic role definitions and branch-scoped security for universities and colleges.

---

## 2. Detailed Table Specifications

### 1. `users`
* **Use Case**: Primary user account table storing authentication credentials, contact details, user roles (`CANDIDATE`, `RECRUITER`, `ORGANIZATION`, `ADMIN`), and password reset OTP verification tokens.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `email` | `VARCHAR(255)` | NO | Unique user email address |
| `phone` | `VARCHAR(20)` | YES | Unique phone number |
| `password_hash` | `VARCHAR(255)` | NO | BCrypt encrypted password string |
| `full_name` | `VARCHAR(255)` | NO | User full legal name |
| `role` | `VARCHAR(30)` | NO | Role enum (`CANDIDATE`, `RECRUITER`, `ORGANIZATION`, `ADMIN`) |
| `active` | `BOOLEAN` | NO | Account active status (default: `TRUE`) |
| `reset_token` | `VARCHAR(10)` | YES | 6-digit OTP verification token for password reset |
| `reset_token_expires_at` | `TIMESTAMP` | YES | Expiration timestamp for reset OTP token |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |
| `updated_at` | `TIMESTAMP` | YES | Record last modification timestamp |

---

### 2. `candidate_profiles`
* **Use Case**: Stores candidate track details (`FRESHER` vs `EXPERIENCED`), UAN number, claimed experience years, internal EPFO-verified backend experience months, college/degree info, skills list, and resume URL.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `experience_track` | `VARCHAR(30)` | NO | Track enum (`FRESHER`, `EXPERIENCED`) |
| `years_of_experience` | `INT` | YES | Claimed years of experience |
| `uan_number` | `VARCHAR(30)` | YES | PF Universal Account Number (UAN) |
| `verified_backend_exp_months` | `INT` | YES | Internal EPFO-verified experience in months (hidden from candidate API) |
| `college_name` | `VARCHAR(255)` | YES | College / University name (Fresher track) |
| `degree_branch` | `VARCHAR(255)` | YES | Degree & Branch (e.g. B.Tech Computer Science) |
| `passout_year` | `INT` | YES | Year of graduation |
| `skills_list` | `TEXT` | YES | Comma-separated list of candidate skills |
| `resume_url` | `VARCHAR(512)` | YES | Document URL for candidate resume |
| `location` | `VARCHAR(150)` | YES | Candidate location or city |
| `headline` | `VARCHAR(255)` | YES | Professional headline |
| `linkedin_url` | `VARCHAR(255)` | YES | LinkedIn profile URL |
| `github_url` | `VARCHAR(255)` | YES | GitHub profile URL |
| `portfolio_url` | `VARCHAR(255)` | YES | Personal portfolio website URL |
| `bio` | `TEXT` | YES | Short biography / about me section |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |
| `updated_at` | `TIMESTAMP` | YES | Record last modification timestamp |

---

### 3. `candidate_search_index` (Specialized Indexing Table)
* **Use Case**: **Read-Optimized Candidate Search Projection**. Solves the performance bottleneck of querying millions of candidates. Recruiter filtering on candidate performance, verified experience, skill score percentiles, and availability is queried directly against this table without performing multi-table SQL JOIN operations across `users`, `candidate_profiles`, `candidate_scores`, and `exams`.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `candidate_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `candidate_name` | `VARCHAR(255)` | NO | Cached candidate full name |
| `experience_track` | `VARCHAR(30)` | NO | Experience track (`FRESHER` / `EXPERIENCED`) |
| `verified_experience_months` | `INT` | NO | Indexed EPFO verified experience in months |
| `overall_score_percentage` | `DOUBLE` | NO | Aggregated overall assessment score percentage |
| `verification_status` | `VARCHAR(50)` | NO | Passport status (`VERIFIED_PASSED`, `NEEDS_IMPROVEMENT`, `NO_TESTS_TAKEN`) |
| `skills_indexed` | `TEXT` | YES | Indexed skill keywords for full-text search |
| `last_updated_at` | `TIMESTAMP` | NO | Timestamp when index projection was last updated |

---

### 4. `candidate_settings`
* **Use Case**: Stores user notification preferences, alert channels, privacy options, and subscription settings for candidates.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE (UNIQUE) |
| `email_notifications` | `BOOLEAN` | NO | Enable/disable email notifications (Default: `TRUE`) |
| `sms_notifications` | `BOOLEAN` | NO | Enable/disable SMS alerts (Default: `FALSE`) |
| `push_notifications` | `BOOLEAN` | NO | Enable/disable browser/mobile push alerts (Default: `TRUE`) |
| `test_reminders` | `BOOLEAN` | NO | Enable/disable upcoming exam & slot alerts (Default: `TRUE`) |
| `interview_invites` | `BOOLEAN` | NO | Enable/disable recruiter interview notifications (Default: `TRUE`) |
| `application_updates` | `BOOLEAN` | NO | Status updates on job applications & drives (Default: `TRUE`) |
| `job_offers` | `BOOLEAN` | NO | Alerts for direct recruiter & campus offers (Default: `TRUE`) |
| `marketing_updates` | `BOOLEAN` | NO | Platform marketing & promotion announcements (Default: `FALSE`) |
| `newsletter` | `BOOLEAN` | NO | Career tips & ProveyU weekly newsletter (Default: `FALSE`) |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |
| `updated_at` | `TIMESTAMP` | YES | Record last modification timestamp |

---

### 5. `companies`
* **Use Case**: Stores recruiter employer company details, verified GSTIN, trade name, state, website domain, and GST verification status (`PENDING`, `VERIFIED`, `REJECTED`).

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `legal_name` | `VARCHAR(255)` | NO | Company legal registered name |
| `trade_name` | `VARCHAR(255)` | YES | Trade name / Doing-Business-As name |
| `gstin` | `VARCHAR(15)` | NO | Unique 15-character GSTIN number |
| `registered_address` | `TEXT` | YES | Registered official address |
| `state` | `VARCHAR(100)` | YES | State of registration |
| `website_domain` | `VARCHAR(255)` | YES | Corporate website domain |
| `verification_status` | `VARCHAR(30)` | NO | Status (`PENDING`, `VERIFIED`, `REJECTED`) |
| `verified_at` | `TIMESTAMP` | YES | Timestamp of GST verification approval |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |
| `updated_at` | `TIMESTAMP` | YES | Record last modification timestamp |

---

### 6. `recruiter_profiles`
* **Use Case**: Maps recruiter user accounts to their verified employer company and stores professional contact information, hiring preferences, and target city preferences.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `company_id` | `VARCHAR(36)` | YES | Foreign Key -> `companies(id)` |
| `designation` | `VARCHAR(255)` | YES | Recruiter designation (e.g. TA Lead) |
| `work_email` | `VARCHAR(255)` | YES | Corporate email address |
| `work_email_verified_at` | `TIMESTAMP` | YES | Timestamp of work email verification |
| `seats_purchased` | `INT` | NO | Number of seats purchased |
| `company_name` | `VARCHAR(255)` | YES | Company / Agency name |
| `city` | `VARCHAR(100)` | YES | Recruiter HQ / Location city |
| `org_type` | `VARCHAR(50)` | YES | Organization type (e.g. agency, corporate) |
| `website` | `VARCHAR(255)` | YES | Company Website URL |
| `headline` | `VARCHAR(255)` | YES | Company Tagline / Headline |
| `about` | `TEXT` | YES | About Organization & Hiring Focus |
| `avatar` | `VARCHAR(255)` | YES | Company Logo or Avatar URL |
| `linkedin` | `VARCHAR(255)` | YES | LinkedIn Profile URL |
| `hiring_volume` | `VARCHAR(255)` | YES | Monthly hiring volume target |
| `candidate_level` | `VARCHAR(255)` | YES | Candidate experience level target |
| `primary_track` | `VARCHAR(255)` | YES | Primary verification track focus |
| `preferred_cities` | `VARCHAR(255)` | YES | Target placement cities |
| `custom_notes` | `TEXT` | YES | Custom hiring criteria notes |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |

---

### 7. `organization_profiles`
* **Use Case**: Stores institutional profiles for colleges, universities, or training institutes registered under the `ORGANIZATION` role.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `legal_name` | `VARCHAR(255)` | NO | Institution legal name |
| `org_type` | `VARCHAR(50)` | NO | Type enum (`COLLEGE`, `UNIVERSITY`, `TRAINING_INSTITUTE`) |
| `code_or_gstin` | `VARCHAR(100)` | YES | Registration code or GSTIN |
| `contact_email` | `VARCHAR(255)` | YES | Contact email address |
| `contact_phone` | `VARCHAR(30)` | YES | Contact phone number |
| `address` | `TEXT` | YES | Physical institution address |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |

---

### 8. `organization_candidates`
* **Use Case**: Maps individual candidate students onboarded under a college/organization account.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `organization_user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` (Organization) |
| `candidate_user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` (Candidate) |
| `roll_number_or_id` | `VARCHAR(100)` | YES | College Roll Number / Student ID |
| `batch_year` | `VARCHAR(30)` | YES | Academic batch year (e.g., "2024-2028") |
| `created_at` | `TIMESTAMP` | NO | Onboarding timestamp |

---

### 9. `skills`
* **Use Case**: Master catalog of technical skills assessed across exam modules.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `name` | `VARCHAR(100)` | NO | Unique skill name (e.g. Java, Python, SQL) |
| `category` | `VARCHAR(100)` | YES | Skill category (e.g. Backend, Database) |
| `description` | `TEXT` | YES | Brief skill description |

---

### 10. `exams`
* **Use Case**: Assessment exam catalog storing title, duration, total marks, passing percentage, and exam registration fee.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `title` | `VARCHAR(255)` | NO | Exam title |
| `description` | `TEXT` | YES | Detailed exam instructions |
| `duration_minutes` | `INT` | NO | Total duration in minutes |
| `total_marks` | `DOUBLE` | NO | Total maximum marks |
| `passing_score` | `DOUBLE` | NO | Minimum passing marks requirement |
| `fee_amount` | `DOUBLE` | NO | Exam fee amount in INR |

---

### 11. `exam_sections`
* **Use Case**: Breakdown of multi-skill assessment exams into distinct sections, each testing a specific skill with designated duration and marks.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `exam_id` | `VARCHAR(36)` | NO | Foreign Key -> `exams(id)` |
| `section_name` | `VARCHAR(255)` | NO | Section title (e.g. "Section A: Core Java") |
| `duration_minutes` | `INT` | NO | Section duration limit in minutes |
| `total_marks` | `DOUBLE` | NO | Section maximum marks |
| `skill_id` | `VARCHAR(36)` | NO | Foreign Key -> `skills(id)` |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |

---

### 12. `candidate_scores`
* **Use Case**: Records candidate exam results, score achieved, max score, skill tested, pass status, and certificate reference.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `candidate_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `exam_id` | `VARCHAR(36)` | NO | Foreign Key -> `exams(id)` |
| `skill_id` | `VARCHAR(36)` | NO | Foreign Key -> `skills(id)` |
| `score` | `DOUBLE` | NO | Achieved score |
| `max_score` | `DOUBLE` | NO | Maximum possible score |
| `passed` | `BOOLEAN` | NO | Pass/Fail boolean flag |
| `created_at` | `TIMESTAMP` | NO | Assessment completion timestamp |

---

### 13. `test_centers`
* **Use Case**: Stores physical examination centers, location address, city, state, pincode, and operational capacity.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `center_name` | `VARCHAR(255)` | NO | Test center facility name |
| `city` | `VARCHAR(100)` | NO | City location |
| `state` | `VARCHAR(100)` | NO | State location |
| `pincode` | `VARCHAR(10)` | NO | Postal pincode |
| `address` | `TEXT` | NO | Full physical address |
| `active` | `BOOLEAN` | NO | Operational status flag |

---

### 14. `rooms`
* **Use Case**: Rooms or testing labs within a physical test center with seating capacity.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `test_center_id` | `VARCHAR(36)` | NO | Foreign Key -> `test_centers(id)` |
| `room_number` | `VARCHAR(50)` | NO | Room / Lab identifier |
| `capacity` | `INT` | NO | Seating capacity |

---

### 15. `date_slots`
* **Use Case**: Date and time slot inventory for candidate exam bookings.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `room_id` | `VARCHAR(36)` | NO | Foreign Key -> `rooms(id)` |
| `exam_id` | `VARCHAR(36)` | NO | Foreign Key -> `exams(id)` |
| `slot_date` | `DATE` | NO | Date of exam slot |
| `start_time` | `TIME` | NO | Slot start time |
| `end_time` | `TIME` | NO | Slot end time |
| `total_seats` | `INT` | NO | Total available seats |
| `available_seats` | `INT` | NO | Remaining available seats |

---

### 16. `bookings`
* **Use Case**: Manages candidate exam slot reservations, holding seat locks during payment, status lifecycle (`HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`), and 15-minute lock expiration.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `candidate_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `slot_id` | `VARCHAR(36)` | NO | Foreign Key -> `date_slots(id)` |
| `status` | `VARCHAR(30)` | NO | Booking status (`HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED`) |
| `held_at` | `TIMESTAMP` | NO | Hold creation timestamp |
| `expires_at` | `TIMESTAMP` | NO | Lock expiration timestamp (15 min after hold) |
| `confirmed_at` | `TIMESTAMP` | YES | Payment confirmation timestamp |

---

### 17. `interview_invites`
* **Use Case**: Stores recruiter interview invitations issued to candidates, job title, description, recruiter salary LPA range, offered package range, invitation response status (`PENDING`, `ACCEPTED`, `DECLINED`), and candidate placement lifecycle status (`IN_PROCESS`, `HIRED`, `REJECTED`).

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `recruiter_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` (Recruiter) |
| `company_id` | `VARCHAR(36)` | YES | Foreign Key -> `companies(id)` |
| `candidate_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` (Candidate) |
| `exam_id` | `VARCHAR(36)` | YES | Foreign Key -> `exams(id)` |
| `job_title` | `VARCHAR(255)` | NO | Job position title |
| `job_description` | `TEXT` | YES | Job responsibilities & details |
| `min_salary_lpa` | `DOUBLE` | YES | Minimum salary LPA offered |
| `max_salary_lpa` | `DOUBLE` | YES | Maximum salary LPA offered |
| `package_range` | `VARCHAR(100)` | YES | Display string for package (e.g., "12.0 - 18.0 LPA") |
| `status` | `VARCHAR(30)` | NO | Candidate response status (`PENDING`, `ACCEPTED`, `DECLINED`) |
| `placement_status` | `VARCHAR(30)` | NO | Placement status (`IN_PROCESS`, `HIRED`, `REJECTED`) |
| `created_at` | `TIMESTAMP` | NO | Invite creation timestamp |
| `updated_at` | `TIMESTAMP` | YES | Invite last update timestamp |

---

### 18. `chat_messages`
* **Use Case**: Stores real-time and historical chat messages, file attachments (PDFs, docs, images), delivery states (`SENT`, `DELIVERED`, `READ`), and read receipts between recruiters and candidates.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `sender_id` | `CHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE |
| `receiver_id` | `CHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE |
| `message` | `TEXT` | YES | Text body of the message (NULL if file only) |
| `message_type` | `VARCHAR(20)` | NO | Enum: `TEXT`, `FILE` (Default: `TEXT`) |
| `file_path` | `VARCHAR(500)` | YES | Server storage path for uploaded attachments |
| `file_name` | `VARCHAR(255)` | YES | Original client file name |
| `file_size` | `BIGINT` | YES | File size in bytes |
| `file_content_type` | `VARCHAR(100)` | YES | MIME type (e.g. `application/pdf`, `image/png`) |
| `status` | `VARCHAR(20)` | NO | Delivery status: `SENT`, `DELIVERED`, `READ` |
| `read_at` | `DATETIME` | YES | Timestamp when recipient read the message |
| `created_at` | `DATETIME` | NO | Message dispatch timestamp |
| `updated_at` | `DATETIME` | NO | Last modification timestamp |

---

### 19. `notifications`
* **Use Case**: In-app notifications and alerts delivered to users for exam bookings, interview invitations, campus placement drives, and system updates.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE |
| `type` | `VARCHAR(50)` | NO | Notification type (e.g. `INTERVIEW_INVITE`, `BOOKING_CONFIRMED`, `DRIVE_ALERT`, `INFO`) |
| `message` | `TEXT` | NO | Formatted notification text content |
| `is_read` | `BOOLEAN` | NO | Read indicator flag (Default: `FALSE`) |
| `created_at` | `TIMESTAMP` | NO | Notification dispatch timestamp |

---

### 20. `flyway_schema_history`
* **Use Case**: Managed automatically by Flyway to track executed database migrations (`V1` through `V17`).

| Field Name | Data Type | Nullable | Description |
| :--- | :--- | :--- | :--- |
| `installed_rank` | `INT` | NO | Execution sequence rank |
| `version` | `VARCHAR(50)` | YES | Migration version string (e.g. `V1`, `V8`, `V9`, `V17`) |
| `description` | `VARCHAR(200)` | NO | Migration script name |
| `type` | `VARCHAR(20)` | NO | Migration type (`SQL`) |
| `script` | `VARCHAR(1000)` | NO | Migration SQL filename |
| `checksum` | `INT` | YES | SQL script checksum |
| `installed_by` | `VARCHAR(100)` | NO | DB user who executed migration |
| `installed_on` | `TIMESTAMP` | NO | Execution timestamp |
| `execution_time` | `INT` | NO | Execution time in milliseconds |
| `success` | `TINYINT(1)` | NO | Success boolean flag |

---

## 3. Placement Management Subsystem (Campus Recruitment & Dynamic Custom RBAC)

### 21. `college_custom_roles`
* **Use Case**: Enables colleges and institutions to define dynamic custom roles without rigid/hardcoded enum restrictions. Organizations create specialized roles (e.g., "Branch Placement Lead", "Department HOD", "Faculty Coordinator") with granular access control and branch scoping.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `organization_id` | `CHAR(36)` | NO | Foreign Key -> `organizations(id)` ON DELETE CASCADE |
| `role_name` | `VARCHAR(100)` | NO | Custom dynamic role title (e.g., "Lead CSE Coordinator", "HOD - IT") |
| `description` | `VARCHAR(255)` | YES | Purpose and responsibilities of this custom role |
| `assigned_branch` | `VARCHAR(100)` | NO | Branch scope restriction (e.g. "Computer Science & Engineering", or "ALL") |
| `can_view_students` | `TINYINT(1)` | NO | Permission: View student roster and academic records (Default: 1) |
| `can_edit_students` | `TINYINT(1)` | NO | Permission: Modify student details and verify marks (Default: 1) |
| `can_manage_placements` | `TINYINT(1)` | NO | Permission: Record offers, schedule drives, update status (Default: 0) |
| `can_invite_team` | `TINYINT(1)` | NO | Permission: Invite or configure team members (Default: 0) |
| `can_export_reports` | `TINYINT(1)` | NO | Permission: Export student Excel/CSV data & KPI summaries (Default: 1) |
| `access_percentage` | `VARCHAR(30)` | YES | Display access level (e.g., "85% Access", "Full Admin Access") |
| `created_at` | `DATETIME` | NO | Creation timestamp |
| `updated_at` | `DATETIME` | NO | Last update timestamp |

---

### 22. `college_team_members`
* **Use Case**: Stores institutional faculty, staff, and coordinators assigned to a college. Each member references an organization user and an optional dynamic custom role from `college_custom_roles`.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `organization_id` | `CHAR(36)` | NO | Foreign Key -> `organizations(id)` ON DELETE CASCADE |
| `user_id` | `CHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE |
| `custom_role_id` | `CHAR(36)` | YES | Foreign Key -> `college_custom_roles(id)` ON DELETE SET NULL |
| `name` | `VARCHAR(150)` | NO | Team member full name |
| `email` | `VARCHAR(190)` | NO | Institutional contact email address |
| `phone` | `VARCHAR(25)` | YES | Contact telephone number |
| `role_name` | `VARCHAR(100)` | NO | Dynamic custom role title created by college (Default: "Custom Role") |
| `assigned_branch` | `VARCHAR(100)` | NO | Assigned department branch scope (e.g., "Information Technology", "ALL") |
| `designation` | `VARCHAR(150)` | YES | Academic designation (e.g., "Associate Professor", "Placement Head") |
| `access_level` | `VARCHAR(50)` | YES | Access badge label (e.g., "90% Access") |
| `status` | `VARCHAR(30)` | NO | Member status (`ACTIVE`, `PENDING_INVITE`, `INACTIVE`) |
| `last_active` | `VARCHAR(100)` | YES | Human-readable last activity indicator |
| `created_at` | `DATETIME` | NO | Creation timestamp |
| `updated_at` | `DATETIME` | NO | Last update timestamp |

---

### 23. `student_profiles`
* **Use Case**: Full academic roster and placement tracking for enrolled college students. Stores complete 8-semester SGPAs, aggregate CGPA, tier classification, ProveyU assessment scores, and placed company/role details.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `organization_id` | `CHAR(36)` | NO | Foreign Key -> `organizations(id)` ON DELETE CASCADE |
| `user_id` | `CHAR(36)` | NO | Foreign Key -> `users(id)` ON DELETE CASCADE (Student user account) |
| `roll_number` | `VARCHAR(50)` | NO | Institutional roll/registration number (Unique per organization) |
| `first_name` | `VARCHAR(100)` | NO | Student first name |
| `last_name` | `VARCHAR(100)` | NO | Student surname / family name |
| `email` | `VARCHAR(190)` | NO | Student college email address |
| `phone` | `VARCHAR(25)` | YES | Student contact mobile phone |
| `dob` | `VARCHAR(20)` | YES | Date of birth (YYYY-MM-DD) |
| `gender` | `VARCHAR(20)` | YES | Gender (Male, Female, Other) |
| `aadhar_number` | `VARCHAR(50)` | YES | Government Aadhaar identification |
| `pan_number` | `VARCHAR(50)` | YES | Government PAN tax ID |
| `residential_address` | `TEXT` | YES | Student current residential address |
| `college_name` | `VARCHAR(255)` | YES | Enrolled institution name |
| `batch_year` | `VARCHAR(20)` | NO | Graduation batch year (e.g., "2024") |
| `branch` | `VARCHAR(100)` | NO | Department branch (e.g., "Computer Science & Engineering") |
| `degree` | `VARCHAR(100)` | NO | Degree qualification (e.g., "B.Tech", "M.Tech", "MCA") |
| `tenth_percentage` | `DOUBLE` | YES | 10th standard board percentage |
| `twelfth_percentage` | `DOUBLE` | YES | 12th standard / Diploma percentage |
| `active_backlogs` | `INT` | NO | Number of live uncleared backlogs (Default: 0) |
| `gap_years` | `INT` | NO | Educational career gap years (Default: 0) |
| `grad_date` | `VARCHAR(50)` | YES | Expected or actual graduation date |
| `sem1_sgpa` - `sem8_sgpa` | `DOUBLE` | YES | Semester 1 through Semester 8 SGPA scores |
| `aggregate_cgpa` | `DOUBLE` | NO | Automatically calculated mean CGPA across active semesters |
| `tier` | `VARCHAR(20)` | NO | Academic tier classification (`Tier 1` >= 8.5, `Tier 2` >= 7.0, `Tier 3` < 7.0) |
| `tier_badge` | `VARCHAR(50)` | YES | UI badge class (`badge-tier-1`, `badge-tier-2`, `badge-tier-3`) |
| `prove_yu_score` | `VARCHAR(20)` | YES | Platform evaluation score (e.g. "75/100") |
| `coding_score` | `INT` | YES | Platform coding assessment percentile / marks |
| `aptitude_score` | `INT` | YES | Platform aptitude percentile / marks |
| `comm_score` | `INT` | YES | Communication and soft skills rating |
| `placement_status` | `VARCHAR(30)` | NO | Current placement state (`Unplaced`, `Placed`, `In Process`) |
| `placed_company` | `VARCHAR(150)` | YES | Name of company where candidate was placed (e.g., "TCS", "Wipro") |
| `placed_role` | `VARCHAR(150)` | YES | Position offered (e.g., "Associate Software Engineer") |
| `placed_ctc` | `VARCHAR(50)` | YES | Annual compensation offered (e.g., "₹6.5L") |
| `package_category` | `VARCHAR(100)` | YES | Category (e.g., "Dream (5-10 LPA)", "Super Dream (>10 LPA)") |
| `bond_duration` | `VARCHAR(50)` | YES | Employment bond obligation (e.g., "No Bond", "1 Year") |
| `offer_date` | `DATE` | YES | Date when offer letter was released |
| `joining_date` | `DATE` | YES | Scheduled company onboarding date |
| `recruitment_source` | `VARCHAR(100)` | YES | Placement channel (`On-Campus`, `Off-Campus`, `Pool Campus`) |
| `linkedin_url` | `VARCHAR(255)` | YES | Candidate LinkedIn URL |
| `github_url` | `VARCHAR(255)` | YES | Candidate GitHub portfolio URL |
| `portfolio_url` | `VARCHAR(255)` | YES | Candidate website or personal project portfolio |
| `resume_uploaded` | `TINYINT(1)` | NO | Boolean flag indicating whether student has an active resume attached |
| `created_at` | `DATETIME` | NO | Creation timestamp |
| `updated_at` | `DATETIME` | NO | Last modification timestamp |

---

### 24. `placement_drives`
* **Use Case**: Campus recruitment drives scheduled by the college placement cell, including company details, eligibility filters, package details, and selection pipeline stages.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `organization_id` | `CHAR(36)` | NO | Foreign Key -> `organizations(id)` ON DELETE CASCADE |
| `company_name` | `VARCHAR(150)` | NO | Visiting company name |
| `company_website` | `VARCHAR(255)` | YES | Company portal link |
| `logo_url` | `VARCHAR(255)` | YES | Company corporate logo URL |
| `industry` | `VARCHAR(100)` | YES | Industry domain (IT Services, Fintech, Automotive, etc.) |
| `roles` | `VARCHAR(150)` | NO | Hiring position(s) |
| `package_lpa` | `DOUBLE` | NO | Annual CTC in Lakhs Per Annum |
| `min_cgpa` | `DOUBLE` | YES | Minimum CGPA cutoff for drive eligibility |
| `max_backlogs` | `INT` | YES | Maximum allowed active backlogs |
| `max_gap_years` | `INT` | YES | Maximum allowed educational career gaps |
| `min_tenth_percentage` | `DOUBLE` | YES | Minimum 10th standard percentage cutoff |
| `min_twelfth_percentage` | `DOUBLE` | YES | Minimum 12th standard percentage cutoff |
| `eligible_branches` | `TEXT` | YES | JSON array or comma-separated list of allowed departments |
| `selection_rounds` | `TEXT` | YES | JSON array of drive rounds (OA, Technical Interview, HR) |
| `drive_mode` | `VARCHAR(50)` | YES | `On-Campus`, `Virtual`, `Off-Campus` |
| `status` | `VARCHAR(50)` | NO | Drive status (`Scheduled`, `In-Progress`, `Completed`, `Cancelled`) |
| `created_at` | `DATETIME` | NO | Creation timestamp |
| `updated_at` | `DATETIME` | NO | Last modification timestamp |

---

### 25. `placement_records`
* **Use Case**: Audit history and individual offer records associated with each student profile.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CHAR(36)` | NO | Primary Key (UUID) |
| `organization_id` | `CHAR(36)` | NO | Foreign Key -> `organizations(id)` ON DELETE CASCADE |
| `student_profile_id` | `CHAR(36)` | NO | Foreign Key -> `student_profiles(id)` ON DELETE CASCADE |
| `drive_id` | `CHAR(36)` | YES | Foreign Key -> `placement_drives(id)` ON DELETE SET NULL |
| `company_name` | `VARCHAR(150)` | NO | Company offering employment |
| `role_offered` | `VARCHAR(150)` | NO | Job title |
| `ctc` | `VARCHAR(50)` | NO | Compensation string (e.g., "₹7.5 LPA") |
| `package_category` | `VARCHAR(100)` | YES | Category (Mass Recruiter, Dream, Super Dream) |
| `bond_duration` | `VARCHAR(50)` | YES | Bond term (e.g., "1 Year", "No Bond") |
| `offer_date` | `DATE` | YES | Offer letter issue date |
| `joining_date` | `DATE` | YES | Planned joining date |
| `recruitment_source` | `VARCHAR(100)` | YES | Drive channel |
| `status` | `VARCHAR(50)` | NO | Status (`OFFERED`, `ACCEPTED`, `DECLINED`, `JOINED`) |
| `created_at` | `DATETIME` | NO | Creation timestamp |
| `updated_at` | `DATETIME` | NO | Last modification timestamp |
