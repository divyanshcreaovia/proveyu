# College Placement Portal: audit, architecture & runbook

Branch: `feature/college-portal`. The portal is wired end to end: Angular 21 → Spring Boot 3.2 → MySQL 8.

## 1. Step 0 audit (verified by running the code)

| # | Claim | Finding | Fix |
|---|-------|---------|-----|
| 1 | Two V17 migrations | **True.** A fresh DB failed with `Found more than one migration with version 17`. Worse, both files declared an FK named `fk_student_user`, so on any DB only one of them could ever apply. | `V17__placement_module.sql` was renamed to `V18__placement_module.sql`, and its FKs were renamed `fk_pm_*`. It is idempotent (`IF NOT EXISTS`, `INSERT IGNORE`). V18 never ran anywhere because Flyway refused to start, so editing it is safe. |
| 1b | (found during the audit) V15 blocks a fresh DB | **True.** V15 seeds `candidate_profiles` for 6 user ids that exist only in the shared DB, so it fails with an FK error. | **Not edited** (it is already applied in the shared DB). Dev/test use `FreshDatabaseV15Callback` (profiles `dev`,`test` only), which inserts 6 *disabled* placeholder users just before V15. The proper fix is in `docs/proposed-V15-fresh-db-fix.patch`, for your decision. The checksum change is absorbed by the existing `flyway.repair()`. |
| 2 | Two placement models | **True.** `placement_drive`/`student` (`/api/v1/placement`) and `placement_drives`/`student_profiles` (`/api/v1/college`). Nothing else depended on them. | One model: `college_*` tables (V19). V20 copies data from **both** legacy models (it also tolerates a DB that has only one of them), then **renames** the old tables to `legacy_*` (nothing dropped). The `com.proveyu.placement` package and its tests were deleted. One API surface: `/api/v1/college/**`. |
| 3 | No role checks | **True.** There were no `@PreAuthorize` checks. `/recruiter/**` and `/test/**` were `permitAll`, and **`/auth/register` let anyone self-register as ADMIN.** | `@EnableMethodSecurity`, URL rules in `SecurityConfig`, `@PreAuthorize` on every college endpoint (READ/WRITE/ADMIN), new roles `COLLEGE_ADMIN/COORDINATOR/VIEWER`. Self-registration is limited to CANDIDATE/RECRUITER. `TestController` is `@Profile("dev")` + ADMIN. JSON 401/403 bodies. |
| 4 | Client-trusted collegeId | **True.** | `CollegeContextResolver` resolves the tenant **only** from the caller's ACTIVE `college_members` row. A role change makes old tokens stale (401 `SESSION_STALE`). Every repository call takes `collegeId`, and other tenants' rows return 404. Covered by `TenancyIsolationIT`. |
| 5 | Secrets in `application.yml` | **True:** DB password, SMTP password, JWT secret, Razorpay values. | Secrets now come only from env vars, with no defaults; the app refuses to start without `JWT_SECRET`. See `Backend/application-example.yml` and `Backend/.env.example`. **Rotate the DB password, the `info@proveyu.com` SMTP password and the JWT secret. They are in git history.** |
| 6 | OTP returned and logged | **True.** The OTP was also generated with `Math.random`, stored in plaintext, and reset accepted the OTP alone. | `SecureRandom` OTP, stored as SHA-256. Reset needs e-mail + OTP. 5-attempt lockout, single use, no account enumeration. Exposed only with `app.dev.expose-tokens=true` (dev profile). `ProductionTokenExposureIT` checks this. |
| 7 | Hard-coded booking date | **True.** | `ReserveSlotUseCase` reads the slot date, time and exam title from `date_slots`/`exams`. |
| 8 | Frontend: URLs, guards, interceptors, redirect | **All true.** | `core/environments` (prod file replacement); all hard-coded `localhost:8080` URLs replaced; `auth/error/loading` interceptors; `auth/role/guest` guards on every portal; login redirects by role. The college pages were 100% mock/localStorage and have been rewritten on real APIs. |

## 2. What was built

**Backend:** `com.proveyu.college` (`domain` / `infrastructure` / `application` / `web` / `security`):

| Area | Endpoints (prefix `/api/v1/college`) |
|------|------|
| Onboarding | `POST auth/register`, `GET auth/invite/{token}`, `POST auth/accept-invite`, `GET me`; `/api/v1/auth/verify-email`, `resend-verification`; admin approval at `/api/v1/admin/colleges` |
| Students | CRUD, filters + paging + sorting, soft delete/restore, status (DEBARRED/OPTED_OUT), document vault + verification, export xlsx/csv/pdf, import (template, dry-run, commit, history, duplicate detection) |
| Companies | CRUD, contacts, MoU, visit history, feedback/rating, link PROVEYU recruiter, invite e-mail, export |
| Drives | CRUD, clone, lifecycle DRAFT→PUBLISHED→ONGOING→COMPLETED/CANCELLED, soft delete, export |
| Eligibility | preview with per-student reasons, audited INCLUDE/EXCLUDE overrides, placement policy engine (`EligibilityEngine`) |
| Applications | TPO registration (override reason for ineligible), bulk shortlist/waitlist/reject, export |
| Rounds | add/reorder/delete, start, results (inline or CSV/XLSX upload), complete + auto-advance |
| Interviews | calendar, single and bulk scheduling, student/panel/room conflict detection, reschedule, status, reminders (scheduler, 24 h ahead) |
| Offers | record (policy check + audited override), lifecycle OFFERED→ACCEPTED/DECLINED/JOINED/REVOKED, offer letter (PDF), PLACED/highest-CTC auto-derived |
| Team | members, invites (7-day token), role/branch-scope changes, revoke/restore, last-admin protection, role matrix |
| Insights | dashboard KPIs + charts + funnel, skill gap, 7 reports (xlsx/csv/pdf incl. NIRF-style summary), scheduled e-mail reports |
| Comms / audit | announcements to filtered groups (in-app + e-mail), chat contacts (existing `/ws/chat`), global search, audit log |
| Student portal | `/api/v1/college/student/**` (CANDIDATE): eligible drives, apply/withdraw, offers accept/decline, documents |

Error body for every failure: `{success:false, timestamp, status, code, errorCode, message, fieldErrors}`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

**Frontend:** `src/app/core` (environments, interceptors, guards, models), `src/app/services` (one service per backend concern; components never use `HttpClient`), `src/app/shared/ui` (data-table, confirm-dialog, modal/drawer, file-upload, empty/error/skeleton states, stat-card, status-badge, stepper, chart, toasts), and `features/college/*`. Components use signals, OnPush and typed reactive forms, with `pm-*` scoped styles. The UI is responsive down to 390 px, has dark mode and Ctrl+K search, and is labelled and keyboard-operable. There are also `/candidate/campus-drives` and `/admin/colleges` pages.

**Add-ons implemented:** placement policy engine (Open / One-offer / Dream tier-upgrade, max applications, max offers, minimum hike); document vault with verification; duplicate detection on import; soft delete + restore; company feedback and ratings; alumni placement history report; skill-gap analytics using PROVEYU verified scores; PROVEYU passport + profile completeness in the student drawer; global search; dark mode; export everywhere; scheduled reports; interview reminders.
**Not implemented:** WhatsApp/SMS (needs a provider), e-signing offer letters, AI ATS scoring (there is no ATS engine in the codebase; the drawer shows profile completeness and links the existing candidate resume instead).

## 3. Running locally

Prerequisites: JDK 17+, Maven 3.8+, Node 18+ (tested on 24), Docker.

```bash
./scripts/start-db.sh                                # MySQL 8 on 127.0.0.1:3310 (Docker volume proveyu-mysql-data)
cp Backend/.env.example Backend/.env                 # set SPRING_DATASOURCE_PASSWORD=proveyu_dev and a JWT_SECRET (openssl rand -base64 64)
./scripts/run-backend.sh                             # http://localhost:8080  (profile dev: Flyway migrates V1..V20)
cd Frontend && npm ci && npm start                   # http://localhost:4200
python3 scripts/seed_demo.py                         # demo data (backend must run with the dev profile)
```

Demo accounts created by the seed:

| Role | Login | Password |
|------|-------|----------|
| TPO / college admin | tpo@demo-college.edu.in | Demo@1234 |
| Coordinator (CSE, IT only) | coordinator@demo-college.edu.in | Team@1234 |
| Viewer | viewer@demo-college.edu.in | Team@1234 |
| Students | aarav.sharma1@demo-college.edu.in (and the next two) | Student@123 |
| Platform admin | admin@proveyu.local | Admin@1234 |

The seed creates 1 college, 4 branches, 3 team members, 50 students, 3 companies (with contacts), 2 drives (1 published with 3 rounds and 20 registered applicants, 1 draft) and 1 off-campus offer.

Dev mode (`SPRING_PROFILES_ACTIVE=dev`) shows verification links, OTPs and invite links on screen, because SMTP is not configured locally. **Never enable `APP_DEV_EXPOSE_TOKENS` in production.** Set `COLLEGE_REQUIRE_APPROVAL=true` to require platform-admin approval of new colleges.

Production build: `cd Frontend && npx ng build` (uses `environment.prod.ts`: same-origin API, `wss://` WebSocket).

## 4. Tests

| Command | What it runs |
|---------|--------------|
| `cd Backend && mvn test` | 67 tests: unit (`EligibilityEngineTest`, `StudentImportServiceTest`, the existing chat/recruiter tests) + Testcontainers MySQL integration (`AuthSecurityIT`, `TenancyIsolationIT`, `PlacementFlowIT`, `ProductionTokenExposureIT`). Needs Docker. |
| `cd Frontend && npx ng test --watch=false` | 21 Vitest tests: TokenStorage, Auth, Student services, auth/role/guest guards, auth/error/loading interceptors, App. |
| `python3 scripts/smoke_test.py` | 74 live API checks against a running backend (full flow + tenancy + roles). |

## 5. Manual test checklist

1. `/college/register`: fill the 3 steps. A TPO e-mail outside the official domain is rejected.
2. Sign in before verifying → "verify your e-mail". Open the verification link (or the dev link) → sign in → dashboard (or the pending page if approval is on; approve at `/admin/colleges` as the platform admin).
3. Settings → add branches (CSE, ECE) and check tiers and placement policy.
4. Students → Import → download template → fill in, including one bad row and one duplicate → **Validate** shows row errors, duplicates and a preview → **Import**. Check that import history updates.
5. Open a student → Passport / Documents (upload a PDF, verify it) / Placement tabs.
6. Companies → add a company plus a primary contact.
7. Drives → New drive → 4 steps (CTC 9 → tier "Dream", eligibility min CGPA and branches) → open it → **Eligibility** shows eligible/ineligible students with reasons; add an override with a reason.
8. **Publish** (eligible students get notified) → sign in as a student → Campus Drives → **Apply**.
9. Back as TPO → Applicants → shortlist → Rounds → add 2 rounds → Start R1 → mark PASS (or upload CSV) → Complete → Start R2 → PASS → Complete → the applicant becomes SELECTED.
10. Interviews tab → schedule; scheduling an overlapping slot shows conflicts; send a reminder; check the calendar month/week view.
11. Offers tab → record the offer → as the student accept it → the student shows **PLACED**.
12. Dashboard KPIs and charts update; Reports → preview and download Excel and PDF; schedule a weekly report.
13. Team → invite a coordinator scoped to CSE → accept the invite link → they only see CSE students and cannot open Settings/Audit (403). Revoke them → their next request is rejected.
14. Audit log shows import, override, policy, offer and role changes.
15. Ctrl+K search, dark mode toggle, phone width (≈390 px).

## 6. Student portal (`/student`)

A separate portal for college students (role `CANDIDATE`), with the same look as the college portal. It has no PROVEYU assessment features.

| Page | What the student does | Connected college-side feature |
|------|------------------------|--------------------------------|
| `/student/signup` | Account activation: e-mail + roll number must match a student the college added, then a 6-digit e-mail code + new password | Students → add / import |
| `/student/login`, `/student/forgot-password` | Sign in (staff accounts are refused here), OTP reset | — |
| Dashboard | Stats, next interview, drives closing soon, latest announcements, placement-readiness checklist | — |
| Campus drives | Eligible drives with reasons when not eligible, details, one-click apply | Drives → publish, eligibility engine |
| My applications | Status, withdraw, round-by-round timeline with PASS/FAIL | Applicants, Rounds tabs |
| Interviews | Upcoming/past slots, join link, **Add to calendar (.ics)** | Interviews tab / calendar |
| Offers | Accept/decline, offer letter PDF, college placement-policy notice | Offers tab, policy engine |
| Announcements | Inbox with unread badge, mark read / mark all | Communication → announcements (now shows "N read") |
| Chat | Live chat with the TPO and the coordinators whose branch scope covers the student | Communication → chat (existing `/ws/chat`) |
| Document locker | Upload once (11 types + own label), view, download, delete until verified | Student drawer → Documents → verify |
| My profile | Academic record read-only; phone, skills, LinkedIn, GitHub editable | Student master |

API: `/api/v1/college/student/**` (CANDIDATE only) and public `POST /api/v1/college/auth/student-signup` (rate-limited).
DB: **`V21__student_portal.sql`** adds `college_announcement_recipients` (per-student inbox + read receipts), new document types and `college_student_documents.label`. Additive only.
Test: `StudentPortalIT` (signup, inbox targeting, contact scoping, locker ownership, profile validation, staff blocked).
Demo: after `seed_demo.py`, students can activate at `/student/signup` (roll number from the Students page) or sign in with `Student@123`.
Announcements sent before V21 have no inbox rows, so students only see announcements sent after the upgrade.
The old `/candidate/campus-drives` URL redirects to `/student/drives`.

## 7. Drive Room (company access to a drive)

Company HR and interview panels work inside one campus drive at `/company` (role `RECRUITER`).

- **TPO:** drive → **Company access** tab → invite by name + work e-mail, choose whether student phone / e-mail / 10th-12th marks are shared, resend, revoke, and read panel scorecards per round.
- **Company:** opens the invitation link (`/company/accept?token=…`), sets a password (or confirms an existing recruiter password), then sees only the drives it was invited to: overview with funnel and live round board, applicants with filters, profile and resume, ZIP of resumes, shortlist / waitlist / reject, start and complete rounds, results table, panel scorecard (technical / communication / attitude 1-5 + result + remarks), release offers to selected students, upload offer letters, Excel/PDF report.
- **One source of truth:** company actions call the same `ApplicationService`, `RoundService` and `OfferService` as the TPO, so auto-advance, placement policy, PLACED status, student notifications and reports behave identically. The audit log names the company person, e.g. "Neha Kapoor (TechNova Solutions)". A scorecard also writes the shared round result (latest wins) with the ratings in the remarks.
- **DB:** `V23__drive_room.sql` adds `college_drive_recruiters` (access + invitation token) and `college_round_scorecards`. No existing table changes.
- **Test:** `DriveRoomIT`. Demo login: `hr@technova-demo.com` / `Recruit@123` at `/company/login` (TechNova drive).
- Not built: QR check-in (by decision), bulk result upload by the company (the TPO's CSV upload still works).

## 8. Things to know

- **Pending feature:** monthly Readiness Assessment dashboard. See [Frontend/src/app/features/college/READINESS_ASSESSMENT.md](../Frontend/src/app/features/college/READINESS_ASSESSMENT.md).

- Legacy tables are kept as `legacy_*`. Drop them once the migrated data is verified on the shared DB.
- `college.require-admin-approval` defaults to **true** (prod); the dev profile sets it to false.
- Chat reuses the existing chat module, which has no participant restrictions of its own; college staff discover contacts via `/api/v1/college/chat/contacts`.
- The candidate portal's sidebar/header icons do not render. This was already the case before this work.
