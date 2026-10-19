# PROVEYU Placement Module Documentation

The **Placement Module** is a self-contained, enterprise-grade recruitment and campus placement system integrated directly into the PROVEYU platform. It powers college placement cell operations, recruitment drive scheduling, automated student eligibility screening, multi-round interview pipelines, shortlist dispatching, and final offer tracking.

---

## 1. Database Architecture & Schema

The placement module schema is defined in `src/main/resources/db/migration/V9__placement_module.sql`. All entities use `CHAR(36)` UUIDs matching PROVEYU's primary key conventions.

### Tables Overview

| Table Name | Description | Key Relationships |
| :--- | :--- | :--- |
| `company` | Companies participating in placement drives | Master table |
| `branch` | College-specific engineering / academic departments | `college_id` -> `organizations(id)` |
| `student` | Student academic placement profile (CGPA, 10th %, 12th %, score, backlogs, gap years, placed status) | `student_id` -> `users(id)`, `college_id` -> `organizations(id)`, `branch_id` -> `branch(branch_id)` |
| `tier` | College-level student tier classification (e.g. Tier 1 >= 80, Tier 2 >= 70, Tier 3 >= 60) | `college_id` -> `organizations(id)` |
| `package_tier_rule` | Mapping from salary ranges `[min, max)` to eligible tiers | `college_id` -> `organizations(id)`, `tier_id` -> `tier(tier_id)` |
| `placement_drive` | Placement drives hosted by a college for a company | `college_id` -> `organizations(id)`, `company_id` -> `company(company_id)` |
| `drive_eligibility` | Specific minimum criteria for a drive (1:1 with drive) | `drive_id` -> `placement_drive(drive_id)` |
| `drive_tier` | Tiers eligible for a drive (many-to-many) | `drive_id`, `tier_id` |
| `drive_branch` | Branches eligible for a drive (empty = all branches) | `drive_id`, `branch_id` |
| `drive_role` | Job roles offered in this drive | `drive_id` |
| `hiring_round` | Master catalog of round types (OA, Coding, GD, Tech 1, Tech 2, HR) | Master table (seeded) |
| `drive_round` | Ordered rounds configured for a drive (counts derived dynamically) | `drive_id`, `round_id` -> `hiring_round(round_id)` |
| `drive_round_student` | Student snapshot in each round (`SHORTLISTED`, `ABSENT`, `ATTENDED`, `SELECTED`, `REJECTED`) | `drive_round_id`, `student_id` -> `users(id)` |
| `placement_offer` | Final job offers awarded after the final round | `drive_id`, `student_id` -> `users(id)` |

---

## 2. Business Rules & Logic

### 1. Derived Tiers (Never Stored)
- A student's tier is derived on the fly: **The best tier (lowest `tier_level`) of their college where `student.score >= tier.min_score`**.
- If a student's score is below the minimum threshold of all college tiers, the student has no tier (`null`).

### 2. Auto-filling Drive Tiers
- When creating a placement drive with an `offered_package` (in LPA), the system automatically looks up `package_tier_rule` for that college matching `offered_package >= min_package_lpa && (max_package_lpa IS NULL || offered_package < max_package_lpa)`.
- The college placement officer can override this selection during drive creation or update.

### 3. Eligibility Engine
A student of that college is eligible for a drive if and only if **ALL** of the following hold:
1. **Tier**: The student's derived tier is in `drive_tier` (if drive tiers are specified).
2. **Branch**: The student's branch is in `drive_branch` (if no branches are selected, all branches are eligible).
3. **Academic Criteria**: For every non-null criterion in `drive_eligibility`:
   - `student.cgpa >= min_cgpa`
   - `student.score >= min_score`
   - `student.tenth_percent >= min_10th_percent`
   - `student.twelfth_percent >= min_12th_percent`
   - `student.active_backlogs <= max_active_backlogs`
   - `student.gap_years <= max_gap_years`
4. **Placed Policy**:
   - `EXCLUDE_ALL`: Skip students with `placed_status = 'PLACED'`.
   - `INCLUDE_ALL`: Include all students regardless of placement status.
   - `ONLY_IF_HIGHER_PACKAGE`: Include placed students only if `drive.offered_package > student.highest_package_lpa`.

### 4. Preview vs. Finalize (Freeze Snapshot)
- `GET /api/v1/placement/drives/{id}/eligible-students`: **Preview** mode. Runs the eligibility engine and returns the eligible student list and count. Modifies zero database rows.
- `POST /api/v1/placement/drives/{id}/finalize`: **Freeze** mode. Freezes the eligible students into Round 1 of `drive_round_student` with status `SHORTLISTED`. This list is an immutable snapshot that remains unaffected by subsequent changes to student records.

### 5. Round Pipeline & Transitions
- Rounds have an explicit `round_number` sequence. Non-consecutive rounds (e.g. 1 -> 3 -> 5) are supported.
- **Round Counts**: Counts for `SHORTLISTED`, `ATTENDED`, `SELECTED`, `REJECTED`, and `ABSENT` are calculated on demand via SQL aggregates, guaranteeing 100% data consistency.
- **Valid Status Transitions**:
  - `SHORTLISTED` $\rightarrow$ `ATTENDED` or `ABSENT`
  - `ATTENDED` $\rightarrow$ `SELECTED` or `REJECTED`
  - *Invalid transitions are blocked.* Students marked `ABSENT` cannot be marked `SELECTED`.
- **Advancement (`/advance`)**:
  - Automatically queries all students with status `SELECTED` in the current round.
  - Inserts them into the next round (by `round_number`) as `SHORTLISTED`.
  - Operates idempotently without duplicating records.

### 6. Automated Email Notifications
- **Student Account Onboarding**: When a college registers a student or performs a bulk Excel/CSV import, an account is created in `users` (`Role.CANDIDATE`) and an email is dispatched via `EmailService` containing their temporary login password.
- **Round Shortlist Emails**: Endpoint `POST /drive-rounds/{id}/send-mail` finds all students in that round with status `SHORTLISTED` where `mail_sent_at IS NULL`, delivers personalized drive notification emails asynchronously, and timestamps `mail_sent_at`.

---

## 3. REST API Reference

All endpoints are prefixed with `/api/v1/placement`.

### Company Management
- `POST /companies`: Create a recruiting company.
- `GET /companies`: List companies.
- `GET /companies/{id}`: Get company details.
- `PUT /companies/{id}`: Update company details.
- `DELETE /companies/{id}`: Remove company.

### Branch Management
- `POST /branches`: Add a college branch (e.g., Computer Science `CSE`).
- `GET /branches`: List branches for the college.
- `DELETE /branches/{id}`: Delete branch.

### Tier & Package Rule Configuration
- `POST /tiers`: Create a score tier (e.g., Level 1, "Tier 1", minScore 80.0).
- `GET /tiers`: List tiers for the college (ordered by tier level ascending).
- `PUT /tiers/{id}`: Update tier.
- `DELETE /tiers/{id}`: Delete tier.
- `POST /package-tier-rules`: Create package rule (e.g., 8 to 15 LPA $\rightarrow$ Tier 1).
- `GET /package-tier-rules`: List package rules.
- `DELETE /package-tier-rules/{id}`: Delete package rule.

### Student Academic Onboarding
- `POST /students`: Register or update a student's academic metrics.
- `POST /students/bulk-import`: Bulk import student rows (creates user + student + sends welcome credentials email).
- `GET /students`: List college students with derived tiers.
- `GET /students/{id}`: Get student academic profile.

### Placement Drives
- `POST /drives`: Create drive (company, package, eligibility criteria, tiers, branches, roles, initial rounds).
- `GET /drives`: List drives (filter by `status` and `driveDate`).
- `GET /drives/{id}`: Get complete drive specification.
- `PUT /drives/{id}`: Update drive criteria and configuration.
- `POST /drives/{id}/cancel`: Cancel drive.
- `GET /drives/{id}/eligible-students`: Preview eligible students + total count.
- `POST /drives/{id}/finalize`: Freeze eligible students into Round 1 as `SHORTLISTED`.

### Rounds & Selection Pipeline
- `POST /drives/{driveId}/rounds`: Add a round to a drive.
- `GET /drives/{driveId}/rounds`: List rounds with real-time candidate counts.
- `POST /drive-rounds/{id}/complete`: Mark round as completed.
- `GET /drive-rounds/{id}/students`: List students in a round (filter by `status`).
- `PATCH /drive-rounds/{id}/students`: Bulk update student statuses and scores (e.g., mark ATTENDED, then mark SELECTED).
- `POST /drive-rounds/{id}/advance`: Advance all `SELECTED` students to the next round.
- `POST /drive-rounds/{id}/send-mail`: Send drive shortlist email to shortlisted students.

### Placement Offers
- `POST /offers/drives/{driveId}`: Convert final-round `SELECTED` students into `placement_offer` rows (`OFFERED`).
- `PATCH /offers/{id}/status`: Update offer status (`ACCEPTED` or `REJECTED`). If accepted, automatically marks student as `PLACED` and updates `highest_package_lpa`.
- `GET /offers`: List offers (filter by `driveId` or `studentId`).

---

## 4. Verification & Testing

Unit tests covering all core business logic are located under `src/test/java/com/proveyu/placement/application/`:
- `PlacementEligibilityEngineTest`: Tier boundary derivation (80, 70, 60, <60), drive tier matching, branch constraints, academic threshold evaluations, and placed policies (`EXCLUDE_ALL`, `INCLUDE_ALL`, `ONLY_IF_HIGHER_PACKAGE`).
- `DriveRoundAdvancementTest`: Progression across skipped rounds (Round 1 $\rightarrow$ Round 3), transition validation (blocking direct selection from shortlist or absent candidates), and idempotency.

Run all tests via Maven:
```bash
mvn test -Dtest=PlacementEligibilityEngineTest,DriveRoundAdvancementTest
```
