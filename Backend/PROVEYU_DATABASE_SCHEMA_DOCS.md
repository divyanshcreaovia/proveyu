# PROVEYU Database Schema Documentation

This document provides a comprehensive, production-grade reference for all database tables in the **PROVEYU** MySQL database (`PROVEYU`).

---

## Table of Contents
1. [Overview](#1-overview)
2. [Detailed Table Specifications](#2-detailed-table-specifications)
   * [1. `users`](#1-users)
   * [2. `candidate_profiles`](#2-candidate_profiles)
   * [3. `candidate_search_index` (Specialized Indexing Table)](#3-candidate_search_index-specialized-indexing-table)
   * [4. `companies`](#4-companies)
   * [5. `recruiter_profiles`](#5-recruiter_profiles)
   * [6. `organization_profiles`](#6-organization_profiles)
   * [7. `organization_candidates`](#7-organization_candidates)
   * [8. `skills`](#8-skills)
   * [9. `exams`](#9-exams)
   * [10. `exam_sections`](#10-exam_sections)
   * [11. `candidate_scores`](#11-candidate_scores)
   * [12. `test_centers`](#12-test_centers)
   * [13. `rooms`](#13-rooms)
   * [14. `date_slots`](#14-date_slots)
   * [15. `bookings`](#15-bookings)
   * [16. `interview_invites`](#16-interview_invites)
   * [17. `flyway_schema_history`](#17-flyway_schema_history)

---

## 1. Overview
The PROVEYU database is designed around transactional integrity, high-concurrency slot reservations, candidate assessment transparency, and recruiter talent discovery.

Key design principles:
- **UUID Primary Keys**: High uniqueness and security across distributed components.
- **Auditing Columns**: `created_at` and `updated_at` timestamps on all business entities.
- **Read-Optimized Projections**: `candidate_search_index` isolates heavy candidate search queries from candidate profile updates.

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

### 4. `companies`
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

### 5. `recruiter_profiles`
* **Use Case**: Maps recruiter user accounts to their verified employer company and stores professional contact information.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `user_id` | `VARCHAR(36)` | NO | Foreign Key -> `users(id)` |
| `company_id` | `VARCHAR(36)` | NO | Foreign Key -> `companies(id)` |
| `designation` | `VARCHAR(255)` | YES | Recruiter designation (e.g. TA Lead) |
| `corporate_email` | `VARCHAR(255)` | YES | Corporate email address |
| `created_at` | `TIMESTAMP` | NO | Record creation timestamp |

---

### 6. `organization_profiles`
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

### 7. `organization_candidates`
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

### 8. `skills`
* **Use Case**: Master catalog of technical skills assessed across exam modules.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `name` | `VARCHAR(100)` | NO | Unique skill name (e.g. Java, Python, SQL) |
| `category` | `VARCHAR(100)` | YES | Skill category (e.g. Backend, Database) |
| `description` | `TEXT` | YES | Brief skill description |

---

### 9. `exams`
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

### 10. `exam_sections`
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

### 11. `candidate_scores`
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

### 12. `test_centers`
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

### 13. `rooms`
* **Use Case**: Rooms or testing labs within a physical test center with seating capacity.

| Field Name | Data Type | Nullable | Description / Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | NO | Primary Key (UUID) |
| `test_center_id` | `VARCHAR(36)` | NO | Foreign Key -> `test_centers(id)` |
| `room_number` | `VARCHAR(50)` | NO | Room / Lab identifier |
| `capacity` | `INT` | NO | Seating capacity |

---

### 14. `date_slots`
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

### 15. `bookings`
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

### 16. `interview_invites`
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

### 17. `flyway_schema_history`
* **Use Case**: Managed automatically by Flyway to track executed database migrations (`V1` through `V7`).

| Field Name | Data Type | Nullable | Description |
| :--- | :--- | :--- | :--- |
| `installed_rank` | `INT` | NO | Execution sequence rank |
| `version` | `VARCHAR(50)` | YES | Migration version string (e.g. `V7`) |
| `description` | `VARCHAR(200)` | NO | Migration script name |
| `type` | `VARCHAR(20)` | NO | Migration type (`SQL`) |
| `script` | `VARCHAR(1000)` | NO | Migration SQL filename |
| `checksum` | `INT` | YES | SQL script checksum |
| `installed_by` | `VARCHAR(100)` | NO | DB user who executed migration |
| `installed_on` | `TIMESTAMP` | NO | Execution timestamp |
| `execution_time` | `INT` | NO | Execution time in milliseconds |
| `success` | `TINYINT(1)` | NO | Success boolean flag |

---

## 9. `candidate_settings` (Settings for Candidates)
- **`id`** (`VARCHAR(36)`): Primary Key (UUID).
- **`user_id`** (`VARCHAR(36)`): Foreign Key (`users.id`). UNIQUE.
- **`email_notifications`** (`BOOLEAN`): Default TRUE.
- **`sms_notifications`** (`BOOLEAN`): Default FALSE.
- **`push_notifications`** (`BOOLEAN`): Default TRUE.
- **`test_reminders`** (`BOOLEAN`): Default TRUE.
- **`interview_invites`** (`BOOLEAN`): Default TRUE.
- **`application_updates`** (`BOOLEAN`): Default TRUE.
- **`job_offers`** (`BOOLEAN`): Default TRUE.
- **`marketing_updates`** (`BOOLEAN`): Default FALSE.
- **`newsletter`** (`BOOLEAN`): Default FALSE.

---

## 10. `notifications` (User Notifications)
- **`id`** (`VARCHAR(36)`): Primary Key (UUID).
- **`user_id`** (`VARCHAR(36)`): Foreign Key (`users.id`).
- **`type`** (`VARCHAR(50)`): Type of notification (e.g. 'urgent', 'info').
- **`message`** (`TEXT`): Notification content.
- **`is_read`** (`BOOLEAN`): Default FALSE.
- **`created_at`** (`TIMESTAMP`): Creation timestamp.
