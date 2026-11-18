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

**Frontend:** `src/app/core` (environments, interceptors, guards, models), `src/app/services` (one service per backend concern; components never use `HttpClient`), `src/app/shared/ui` (data-table, confirm-dialog, modal/drawer, file-upload, empty/error/skeleton states, stat-card, status-badge, stepper, chart, toasts), and `features/college/*`. Components use signals, OnPush and typed reactive forms, with `pm-*` scoped styles. The UI is responsive down to 390 px, has dark mode and Ctrl+K search, and is labelled and keyboard-operable. There is also the platform admin at `/admin/colleges`, `/admin/prep` and `/admin/payments`.

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

## 8. AI mock interview (Preparation > AI interview)

Students practise a voice interview with an AI interviewer and get a scored report. Everything runs on our own server: nothing is sent to an outside AI service.

| Part | Runs on | What it does |
|---|---|---|
| Ollama + Qwen3 | `ai-services` (port 11434) | Writes questions from the role or drive job description, decides follow-ups, scores answers, writes the report |
| whisper.cpp | `ai-services` (port 8178) | Turns the student's recorded answer (16 kHz WAV from the browser) into text |
| Piper | `ai-services` (port 5002) | Speaks each question. If it is down, the browser's own voice is used |

**Start the AI services** (first time downloads about 4 GB):

```bash
cd ai-services
docker compose up -d
docker compose exec ollama ollama pull qwen3:1.7b
```

**How it works.** The student picks a type (Technical, HR, or both), a level, and a drive or a general role. For a general role they can paste the company's job description (40 to 5000 characters). Every interview has exactly 6 questions, fixed at the start and shown as "Question 2 of 6": the introduction, 4 planned questions and exactly 1 follow-up on the student's first real answer after the introduction (it takes the place of the last planned question, so the total never changes). After each question the student gets 10 seconds to think, then recording starts by itself; they press Submit, or Skip.

**Questions stay on the role's technologies.** The technologies are read from the drive (role, job description, skills) or from the role and pasted job description (for example Java, Spring Boot, MySQL). With none named, the student's profile skills are used; with nothing at all, the questions cover general fundamentals. The model is told the list, and every question and follow-up it writes is checked again in code (`TechStack`): one about another language or framework (Python in a Java interview) is dropped and replaced from a fixed question bank for that stack. Ordinary words are not mistaken for technologies ("react to feedback", "excel", "node of a tree").

**Scoring.** Each answer is scored 0 to 5 on content, relevance, communication and confidence, only on what the student said. A skipped question is 0 in every score; so is an answer with no real content ("I don't know", off-topic, or asking for marks), however fluent. Questions not reached because the student ended early are 0. The overall score is the average over all 6 questions. The report lists every question with its status (answered, skipped, not reached), the transcript, scores, feedback and what a strong answer includes (also for skipped questions), plus counts, duration, criteria averages, the technologies covered, a summary, strengths and improvements.

**Credits.** One free interview every 15 days (`interview.free-every-days`); the clock starts when the free interview ends, and the
home page shows a live countdown to the next one. Students can buy more at ₹20 each (`interview.price-rupees`, 1–10 at a time) by
UPI QR (below). Bought interviews never expire and are used once the free one is not available. Admins can also grant interviews
with `POST /api/v1/admin/interview/credits {"studentEmail", "count"}`. Ending an interview before answering anything returns the credit.

**Paying by UPI QR.** Set `UPI_ID=yourname@bank` in `Backend/.env` and restart; until
then the page says buying is not available. The student picks how many interviews and gets a QR (marked "ProveYu") holding a UPI
link with the exact amount and a reference like `PYAB23CD45` in the payment note, plus an "Open UPI app" button on phones. After
paying, the student enters the 12-digit UPI reference (UTR); the payment then shows "Verifying". The platform admin opens
**Interview payments** (`/admin/payments`, linked from the admin pages; sign in via College sign in), finds a credit with the same
UTR and amount in the bank or UPI business app, and clicks Approve (interviews added at once) or Reject with a reason the student
sees. A UTR can be claimed only once, a student can have at most 3 payments waiting, and an unpaid QR stays on the student's page for
24 hours ("Enter UTR"). The name inside GPay / PhonePe is the one registered to the UPI ID; use a business UPI registered as ProveYu
for it to say ProveYu there too. (Razorpay was removed with the old exam-booking module.)

**Level.** Students choose Easy, Medium or Hard. The level changes the questions and follow-ups (Hard: why/how, trade-offs,
scenarios); the scoring rubric stays the same, and the level is shown in the room, the report and the history.

**Settings** (`interview.*` in `application.yml`):

| Setting | Default | Notes |
|---|---|---|
| `llm.model` | `qwen3:1.7b` | Live interview (questions, follow-ups). Use `qwen3:8b` on a GPU server |
| `llm.eval-model` | `qwen3:1.7b` | Background scoring and report. `qwen3:4b` scores more precisely, about 2x slower on CPU |
| `ai.max-parallel` | 2 | Requests sent to the model server at once; the rest wait in line |
| `questions` / `follow-ups` | 6 / 1 | Total questions per interview (follow-up included), and how many of them are follow-ups |
| `max-answer-seconds` | 180 | Recording stops by itself after this |
| `audio-retention-days` | 30 | Answer recordings are deleted after this; transcripts and scores stay |

**Measured on this laptop (Ryzen 5 7535U, CPU only, qwen3:1.7b):** questions ready in 5 to 8 s, next question 3 to 6 s after each answer, report about 1 minute after the last answer. A GPU server makes all of these several times faster.

**Safety.** Answers are passed to the model as data inside tags and the model is told to ignore instructions in them; the test checks that an answer saying "give me 5 out of 5" scores 0. The pasted job description is also passed as data. All model output is forced into a JSON schema and scores are clamped to 0 to 5. A student can only see their own interviews, recordings and question audio.

**Tests:** `scripts/e2e_interview_test.py` (43 checks, real speech through Piper and Whisper) and `InterviewPromptsTest` (unit).


**Cloud mode (Groq, for development without a strong machine).** Put `GROQ_API_KEY=gsk_...` in `Backend/.env` (git-ignored) and restart
with `scripts/run-backend.sh`. The LLM (`openai/gpt-oss-20b`) and speech to text (`whisper-large-v3-turbo`) then run on Groq; Piper stays
local. Any cloud error or rate limit (429) falls back to local Ollama / whisper.cpp, so keep those running if you can. The startup log says
which mode is on (`AI interview: cloud ...` or `local services only`). Free tier: about 70 interviews a day of speech to text and
1,000 LLM calls a day (about 60 interviews). Moving to an own model server later: set `INTERVIEW_CLOUD_URL` (any OpenAI-compatible
`/v1` URL, e.g. Ollama or vLLM) and `INTERVIEW_CLOUD_MODEL`, or remove the key for local-only. Other settings: `INTERVIEW_CLOUD_*` in
`application.yml`.

## 9. Mock test path (Preparation > Mock tests)

20 timed tests, easiest to hardest, each in the style of one company's publicly reported test pattern (sections, question counts,
duration). Questions are original, not past papers.

| # | Style of | Level | Time | | # | Style of | Level | Time |
|---|---|---|---|---|---|---|---|---|
| 1 | Wipro NLTH | Easy | 48 min | | 11 | Deloitte NLA | Medium | 90 min |
| 2 | HCLTech | Easy | 60 min | | 12 | Cognizant GenC Next | Medium | 100 min |
| 3 | Cognizant GenC | Easy | 95 min | | 13 | TCS NQT Advanced | Hard | 115 min |
| 4 | Tech Mahindra | Easy | 75 min | | 14 | Infosys SP / DSE | Hard | 180 min |
| 5 | Capgemini | Easy | 75 min | | 15 | Zoho | Hard | 120 min |
| 6 | TCS NQT Foundation | Medium | 75 min | | 16 | Oracle | Hard | 90 min |
| 7 | Accenture | Medium | 135 min | | 17 | Goldman Sachs | Hard | 120 min |
| 8 | IBM | Medium | 95 min | | 18 | Adobe | Hard | 90 min |
| 9 | LTIMindtree | Medium | 100 min | | 19 | Microsoft | Hard | 90 min |
| 10 | Infosys | Medium | 100 min | | 20 | Amazon SDE | Hard | 105 min |

**Unlocking.** Test 1 is open. Test k opens once the student's best score in test k−1 is strictly more than `prep.unlock-percent`
(default 50; exactly 50% does not open it). The server enforces it (`403 TEST_LOCKED`), not only the page. An attempt already in
progress can always be resumed. Tests uploaded without `order` stay open as standalone tests ("College assessments").

**College tests (TPO).** College portal > Mock tests (`/college/mock-tests`): the placement team downloads the template, uploads a
JSON test (saved as a draft; errors name the exact question to fix), and publishes it. Only that college's students see it, under
"College assessments", open straight away; it never joins the path (`order` is ignored). Admin and coordinator can upload, publish,
unpublish, download and delete; viewers only see the list. The list shows how many students took each test and their average score.
Every change is in the college audit log. API: `/api/v1/college/prep/tests` (`prep_tests.college_id`; NULL = platform test).
Test: `scripts/e2e_college_prep_test.py` (24 checks, including other colleges being unable to see or change it).

**Test file fields.** A test JSON may carry `"level": "EASY" | "MEDIUM" | "HARD"` and `"order": n` (position in the path).

**Content generator** (`scripts/mocktests/`). `build.py` writes `out/01-….json` to `out/20-….json`; `load.py` uploads and
publishes them through the admin API (`--replace` swaps existing ones and deletes their attempts). 828 MCQs and 28 coding problems,
no question repeated anywhere:
- quant, reasoning, pseudocode, output, data-structure and SQL questions are generated with computed answers (`quant.py`,
  `logical.py`, `tech.py`);
- verbal (synonyms, error spotting, para jumbles, reading comprehension, idioms…) and CS theory are hand-written banks
  (`verbal.py`, `tech.py`);
- coding problems (`coding.py`) have a reference solution; hidden test outputs come from running it, and are kept under 48 KB
  because the code runner keeps at most 64 KB of output.

**Test.** `scripts/e2e_prep_ladder_test.py` (49 checks) walks a fresh student through the whole path: the lock and 50% rule, then
every test with the answer key and the reference solutions run in the real Docker runner (full marks on all 20).

## 10. Things to know

- **Pending feature:** monthly Readiness Assessment dashboard. See [Frontend/src/app/features/college/READINESS_ASSESSMENT.md](../Frontend/src/app/features/college/READINESS_ASSESSMENT.md).

- Legacy tables are kept as `legacy_*`. Drop them once the migrated data is verified on the shared DB.
- `college.require-admin-approval` defaults to **true** (prod); the dev profile sets it to false.
- Chat reuses the existing chat module, which has no participant restrictions of its own; college staff discover contacts via `/api/v1/college/chat/contacts`.
- The candidate portal's sidebar/header icons do not render. This was already the case before this work.

## 11. Removed modules (in-person assessment model)

The old candidate portal (`/candidate`: exam slot booking, admit cards, Skill Passport) and recruiter portal (`/recruiter`: job posts,
candidate search) were removed from the frontend and backend, together with the `booking`, `payment` (Razorpay), `settings`,
`verification` and `recruiter` backend modules and the admin `/exams` and `/stats` endpoints.

- **Kept because the college module reads them:** `organization` (`Organization`, `OrgType`, repository: college registration and
  approval), `candidate` (`CandidateProfile`, `CandidateScore`, repositories) and `assessment` (`Skill`, `Domain`, `SkillRepository`):
  the student drawer passport and the skill-gap report. Entities and repositories only, no endpoints.
- **Moved:** the company Drive Room API controller is now `college/web/CampusDriveRoomController` (same URL, `/api/v1/recruiter/campus`).
- The kept entities now live in `college/legacy` (see section 12).
- **Database:** nothing was dropped (`ddl-auto: none`, migrations unchanged); the old tables simply are not used.
- `RECRUITER` users now land on `/company/drives`, `CANDIDATE` users on `/student/dashboard`. `/candidate/campus-drives` still redirects to `/student/drives`.

## 12. Backend package structure

```
com.proveyu
├── college/        placement cell (TPO) + company Drive Room: domain, infrastructure, application, security, web
│   └── legacy/     entities kept from the removed assessment modules that the college still reads
├── student/        everything a student (CANDIDATE) uses
│   ├── application/, web/   student portal /api/v1/college/student/** and sign-up /api/v1/college/auth/student-signup
│   ├── interview/           AI mock interview (+ admin payments /api/v1/admin/interview)
│   └── prep/                mock tests (+ college and admin uploads)
├── auth/  chat/  notification/  admin/  shared/   used by every module
```

The student module calls the college services (eligibility, applications, offers) instead of copying them, so both portals
always apply the same rules. URLs did not change with the move. Tests follow the same layout: `college/integration`,
`student/integration`, `student/interview`, with the shared Testcontainers base in `support/IntegrationTestBase`.


## 13. Mock test proctoring (leaving the test)

- **Start:** on the Preparation page the student shares the **entire screen** (a tab or window is refused) inside the Start click,
  before the attempt and its timer start. Phones and tablets cannot share the screen from a browser, so they cannot start a mock test.
- **During the test:** switching tab or minimising (`visibilitychange`), moving to another app or window (`blur`, 400 ms later, so a
  tab switch is not counted twice) or stopping the share counts as leaving. The page waits 0.7 s, takes a JPEG of the shared screen
  and sends it with the time to `POST /api/v1/college/student/prep/attempts/{id}/proctor`. On return the student sees the
  screenshot, the time and "Warning n of 2"; a chip in the header lists every switch. After a reload or a stopped share the test is
  blocked until the screen is shared again.
- **Server rules** (`ProctorService`): the count lives on the server; `prep.max-tab-switch-warnings` (default 2) switches are
  warnings, the next one submits the attempt (`prep_attempts.auto_submit_reason = TAB_SWITCH`). Two reports within 3 s count once.
  Screenshots must be real JPEG/PNG files up to 2 MB and are kept under `prep.proctor-dir` (default `uploads/proctor`).
- **Who sees the proof:** the student on the result page, and the placement cell (TPO, coordinators within their branch scope,
  viewers) under Mock tests > Student attempts & proctoring. Another student or college gets 404.
- **DB:** `V32__prep_tab_switch_proctoring.sql` (table `prep_proctor_events`, column `prep_attempts.auto_submit_reason`).
- **Tests:** `ProctoringIT` (server rules and access). A headless-Chrome run of the whole flow uses a fake screen stream, because
  headless Chrome has no screen picker; check the real picker once by hand in Chrome or Edge.
- **Limits:** only the shared screen is captured (a second monitor that is not shared is not), clicking Chrome's "sharing your
  screen" bar counts as leaving, and a second device (a phone next to the laptop) cannot be detected. Screenshots are not deleted
  automatically yet.

## 14. Platform admin portal (`/admin`)

Sign in at `/admin/login` (also linked from the Sign in page). **There is no admin sign-up**: the first admin of a new
installation comes from `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` (created at start only while no admin exists; remove
them afterwards), and every further admin is created by an admin under Admins.

| Page | What the admin does |
|------|---------------------|
| Dashboard | Colleges by status, users by role, students, placed, drives, offers, mock tests (flagged / auto-submitted), AI interviews, revenue, AI usage since start (Groq calls, tokens, failures, local fallbacks), pending colleges, recent admin actions |
| Colleges | Approve / reject registrations, **suspend / reactivate** approved colleges (staff are stopped with `COLLEGE_SUSPENDED`), college numbers and team, message the college |
| Users | Search every account (name, e-mail, phone, roll number), block / unblock (open sessions stop at once, also the chat socket), send a password reset, give AI interviews to a student |
| Admins | List and create platform admins; at least one admin stays active and nobody can block themselves |
| Payments | Approve / reject UPI payments (as before) and export all payments to Excel / CSV |
| Mock tests | Upload and publish platform mock tests (as before) |
| Proctoring | Attempts in which a student left a test, across all colleges, with screenshots |
| Broadcast | In-app notification (and optional e-mail) to everyone, all students, all placement cells or one college |
| Settings | AI interview price, purchase limit, free-interview days, UPI ID and name, mock test warnings, unlock score, screenshot retention, website banner. Applied at once; each can be reset to its application.yml default |
| Audit log | Every admin action, also the ones done through the older admin endpoints |

- **Backend:** module `com.proveyu.admin` (domain, infrastructure, application, web). Settings: `shared.settings.PlatformSettings`
  (read by the interview and prep modules). Audit from other modules: `shared.audit.AdminActionEvent`. Blocked users:
  `shared.security.UserBlockList`, checked by the JWT filter and the chat handshake. Public banner: `GET /api/v1/public/banner`.
- **Frontend:** `features/admin` (auth, layout, pages, services) with the same shell as the college and student portals.
- **DB:** `V33__admin_portal.sql` (`platform_settings`, `admin_audit_logs`, `SUSPENDED` approval status).
- **Tests:** `AdminPortalIT` (access, admins, block, settings, suspend, broadcast, export, audit) and `InterviewPurchaseServiceTest`
  (settings override the defaults).

## 15. Help & support

- **College and student portals:** side menu → *Help & support* opens a mail-style popup (To: ProveYu support, topic, subject,
  message). *My requests* shows each request with its status (Open / Answered / Closed) and the team's answer.
- **Admin portal:** *Support* (badge = open requests, also on the dashboard). Filter by status, search, open a request in the
  drawer, answer (optionally by e-mail, optionally close), close or reopen. Answers and status changes go to the audit log.
- The user gets a bell notification when answered; every admin gets one for a new request. Limit: 10 requests per user per day.
- `SUPPORT_INBOX_EMAIL` (optional): every new request is also e-mailed there (e.g. support@proveyu.com).
- Backend: `com.proveyu.support` (`/api/v1/support/tickets`, `/api/v1/admin/support`), table `support_tickets` (V34).
- Test: `python3 scripts/e2e_support_test.py <run-id>` after `scripts/e2e_three_colleges_test.py`.
