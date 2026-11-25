# ProveYu architecture

How the backend is put together, how it keeps data safe, and which background jobs and outside services it uses. For the
endpoint list see [PROVEYU_API_SPECIFICATION_DOCS.md](PROVEYU_API_SPECIFICATION_DOCS.md), for the tables
[PROVEYU_DATABASE_SCHEMA_DOCS.md](PROVEYU_DATABASE_SCHEMA_DOCS.md), for running and testing [../RUN_AND_TEST.md](../RUN_AND_TEST.md),
and for feature details [../docs/COLLEGE_PORTAL.md](../docs/COLLEGE_PORTAL.md).

## 1. The system

```text
Browser (Angular 21, :4300) ──HTTPS/JSON──► Spring Boot 3.2 backend (:8080) ──JDBC──► MySQL 8 (proveyu_college)
        │                                  │  ├─ WebSocket /ws/chat (live chat)
        └──── WebSocket ───────────────────┘  ├─ uploads/ on disk (documents, offer letters, NOCs, chat files, audio, screenshots)
                                              ├─ Judge0 CE on its own VM (mock test code, internal network only)
                                              ├─ Groq API (AI interview LLM + speech-to-text)  ──fallback──► Ollama + whisper.cpp (local)
                                              ├─ Piper (local text-to-speech for interview questions)
                                              ├─ Careerjet API (nightly off-campus job import)
                                              └─ SMTP (e-mails, sent in the background)
```

One Spring Boot application, one database. Five front ends share it: the public website, the college portal (placement cell),
the student portal, the company Drive Room and the platform admin portal.

## 2. Backend modules

Each module is a package under `com.proveyu` with the same layers: `domain` (entities, records, enums), `infrastructure`
(repositories), `application` (services: the rules live here), `web` (controllers: thin).

| Module | What it owns |
|---|---|
| `auth` | Sign-in (JWT), e-mail verification, OTP password reset, logout (token blacklist) |
| `college` | Everything the placement cell does: students, imports, documents, companies, drives, eligibility engine, applications, rounds, interviews, offers and policy, announcements, reports, team and roles, audit log, Drive Room for companies. `college/security` resolves the caller's college. `college/legacy` keeps entities of the older platform that reports still read |
| `student` | Student portal (`application`, `web`), mock tests and proctoring (`prep`), AI mock interview with credits and UPI purchases (`interview`) |
| `offcampus` | Off-campus jobs (posted by the team or imported from Careerjet), fields and student interests, application tracking, NOC requests and the NOC PDF |
| `admin` | Platform admin portal: dashboard, colleges, users, admins, payments, broadcast, settings, audit |
| `support` | Help & support requests and answers |
| `chat` | One-to-one chat (REST + WebSocket) |
| `notification` | Notification bell |
| `shared` | Security config and filters, error handling, `ApiResponse`, e-mail, file storage, platform settings, logging |

The student module calls the college services (eligibility, applications, offers) rather than copying them, so both portals
always apply the same rules. Data access is JPA for entities and `JdbcTemplate` for reporting-style queries (support, off-campus).

## 3. Security

- **Authentication:** `POST /api/v1/auth/login` returns a JWT (24 h by default, `JWT_EXPIRATION_MS`). `JwtAuthenticationFilter`
  reads it on every request; logout blacklists the token (`TokenBlacklistService`, cleaned every 30 minutes). Sign-in requires a
  verified e-mail. Passwords are BCrypt hashes; OTPs are random, stored hashed, single use, with an attempt limit.
- **Roles:** `CANDIDATE` (student), `COLLEGE_ADMIN` (TPO), `COLLEGE_COORDINATOR`, `COLLEGE_VIEWER`, `RECRUITER` (company Drive Room),
  `ADMIN` (platform). URL rules in `SecurityConfig` plus `@PreAuthorize` on each endpoint (`CollegeAccess.READ / WRITE / ADMIN / STUDENT`).
- **College separation:** `CollegeContextResolver` builds the `CollegeContext` (college id, role, branch scope) only from the
  caller's active `college_members` row, never from the request. Every college query filters by that college id; another
  college's ids answer 404. Coordinators only see their branches. Students only reach their own records (documents, NOCs,
  interviews, attempts).
- **Blocking:** `UserBlockList` is checked by the JWT filter and the chat handshake, so a blocked user is stopped at once.
  Suspending a college stops its staff (`COLLEGE_SUSPENDED`).
- **Rate limiting:** `RateLimitingFilter`, sliding window: 60 requests a minute per IP on sign-in routes, 120 a minute per user elsewhere.
- **Uploads:** size and extension limits, and the file's first bytes must match its extension. Files are stored under random names
  and served only through authorised endpoints (never a public folder).
- **Student code:** runs in Judge0 CE's isolate sandbox on a separate VM: no network, CPU, memory and wall-time limits, capped output.
  The backend talks to it through `Judge0Client` (token in a header, never logged). The old Docker runner stays behind
  `code.execution.provider=docker`.
- **Audit:** college staff actions go to `college_audit_logs`, admin actions to `admin_audit_logs`.
- **Dev only:** `APP_DEV_EXPOSE_TOKENS=true` returns OTPs and invite tokens in responses for the test scripts. Must be `false` in production.

## 4. Errors and responses

Every JSON response is `ApiResponse<T>`: `{ success, message, data }`. Failures go through `GlobalExceptionHandler`:
`{ success: false, timestamp, status, code, errorCode, message, fieldErrors }`. Business rules throw `DomainException` with an HTTP
status and a stable error code (for example `NOT_ELIGIBLE`, `NOC_EXISTS`, `RUNNER_BUSY`; the code runner also uses `RUNNER_UNREACHABLE`, `RUNNER_TIMEOUT`, `RUNNER_AUTH`, `RUNNER_INTERNAL`, `RATE_LIMITED` and `INPUT_TOO_LARGE`). Each request is logged with its path,
status and duration (`ApiLoggingFilter`).

## 5. AI interview pipeline

`student/interview`: the student picks a drive JD or pastes one, chooses focus and difficulty, and gets 6 questions with one
follow-up on their answer. Answers are spoken (WAV upload, speech-to-text) or typed; each answer is scored in the background and a
report is built at the end.

`AiGateway` is the single door to the models:

1. **Groq first** (OpenAI-compatible API): `openai/gpt-oss-20b` with strict JSON-schema output, `whisper-large-v3-turbo` for speech.
   Several keys in `GROQ_API_KEY` (comma-separated) are used in turn; a key that hits 429 or 401 rests and is skipped.
2. **Local models** (`AI_LOCAL_MODELS_ENABLED=true`, the default): Ollama `qwen3:1.7b` and whisper.cpp answer when every key fails,
   and **Piper** reads questions aloud. With `false` nothing local is called: Groq only, and the browser's voice reads questions.

Semaphores cap parallel calls (`INTERVIEW_CLOUD_MAX_PARALLEL`, `interview.ai.max-parallel`); silent audio is never sent to Whisper.
Credits: one free interview every `free-every-days`, more bought by UPI and approved by the admin.

## 6. Mock tests and proctoring

`student/prep`: tests are JSON (sections, MCQs, coding questions with sample and hidden cases). The 20-test ladder unlocks the next
test above a score threshold. Coding answers run on Judge0 (one batch per answer, one submission per case); the backend compares output (trailing
spaces ignored). The student must share the whole screen; leaving the test (tab switch, other window, share stopped) is reported
with a screenshot, and after the allowed warnings the attempt is submitted (`ProctorService`). Scoring is done on the server.

## 7. Off-campus jobs

`offcampus`: jobs carry eligibility filters (batch, degree and branch keywords, CGPA, backlogs, colleges) and a field (software,
data, mechanical, ...). `OffCampusRules` decides visibility and who may apply, including the college's off-campus policy. Students
apply on the company site; ProveYu records the click and the student reports the status. With an offer, the student asks the
placement cell for a NOC; approving generates a PDF on the college letterhead (`NocLetter`, PDFBox) or stores a signed copy.
`OffCampusImportService` imports fresher jobs and internships from Careerjet every night as drafts for the team to review.

## 8. Background jobs

| Job | When | Where |
|---|---|---|
| Off-campus import from Careerjet, and closing imported jobs older than 15 days | every night at 00:00 Asia/Kolkata (`OFFCAMPUS_IMPORT_CRON`) | `OffCampusImportService` |
| Interview reminders (24 h ahead) | every 15 minutes | `CampusInterviewService` |
| Scheduled e-mail reports | every 10 minutes (sends those that are due) | `ReportService` |
| End AI interviews left open for 3 hours (credit returned if nothing was answered) | every 15 minutes | `AiInterviewService` |
| Delete old interview audio | daily 03:30 | `AiInterviewService` |
| Delete proctoring screenshots older than the admin's retention setting | daily 03:45 | `ProctorService` |
| Clean the logout token blacklist | every 30 minutes | `TokenBlacklistService` |

E-mails are sent with `@Async`, so a slow SMTP server never slows a request.

## 9. Configuration and migrations

- All settings are in `src/main/resources/application.yml` with environment-variable overrides; secrets come only from the
  environment (`Backend/.env`, git-ignored; template `Backend/.env.example`). The app does not start without `JWT_SECRET`.
- Runtime settings that the admin changes without a restart (interview price, UPI ID, limits, banner) live in `platform_settings`
  (`PlatformSettings`).
- Flyway migrations `V1`..`V38` in `src/main/resources/db/migration` run on start. `ddl-auto: none`: Hibernate never changes the schema.
- Redis and Redisson are on the classpath but switched off (their auto-configuration is excluded); nothing uses them.

## 10. Frontend

Angular 21 standalone components with signals, OnPush and zoneless change detection; Bootstrap 5. `core/` holds auth, guards
(auth, role, guest), interceptors (token, errors, loading) and API helpers; `shared/ui/` the UI kit (modal, tables, status badge,
states, toasts, notification panel); `features/` one folder per portal (`public`, `college`, `student`, `company`, `admin`) plus
shared features (`support`, `offcampus`). Pages talk to the backend only through services.

## 11. Scaling notes

- The app is stateless apart from the chat WebSocket sessions and in-memory semaphores; run several instances behind a load
  balancer with sticky sessions for `/ws/chat`, or move chat fan-out to a broker first.
- `uploads/` is local disk: with more than one instance, move it to shared or object storage.
- Judge0 runs are limited per backend to `JUDGE0_MAX_PARALLEL` (default 4); extra runs queue for up to 20 s, then "judge busy". Students
  get one code run per 5 s each for Run, Code ground and Submit (`CODE_RUN_RATE_LIMIT_SECONDS`). A Submit with only MCQ answers is not
  limited. Admin test inputs can be up to 1 MB; Code ground input is capped at 64 KB. For large drives, add Judge0 workers and move grading to a queue.
- Groq throughput is bounded by the account's rate limits; a paid tier or a second provider is the way to scale interviews.
