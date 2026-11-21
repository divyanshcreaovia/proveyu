# ProveYu: Campus Placement Platform

ProveYu connects a college's placement cell with its students. The college runs drives, rounds and offers. Students apply, track
results, prepare with proctored mock tests and AI mock interviews, and apply to off-campus jobs picked for their field (with a NOC
from the placement cell when they get an offer). The ProveYu team runs the platform from an admin portal.

| Portal | URL (dev) | Who uses it |
|---|---|---|
| Public website | `http://localhost:4300/` | Visitors (For Colleges, For Students, How It Works) |
| Sign in (choose portal) | `http://localhost:4300/login` | Everyone |
| College portal | `/college/login` | TPO, placement coordinators, viewers |
| Student portal | `/student/login` | Students of a registered college |
| Drive Room | `/company/login` | Company HR and interview panels invited to a drive |
| Admin portal | `/admin/login` | ProveYu team. There is no admin sign-up; only an admin can create another admin |

---

## Tech stack

| Part | Technology |
|---|---|
| Frontend | Angular 21 (standalone components, signals, zoneless), Bootstrap 5, TypeScript 5.9 |
| Backend | Spring Boot 3.2.3 (Java 17+, runs on 21), Spring Security with JWT, JPA plus JdbcTemplate, Flyway |
| Database | MySQL 8, database `proveyu_college`, migrations `V1`–`V39`, 54 tables in use (see [finaluseddbschema.sql](finaluseddbschema.sql)) |
| AI interview | Groq cloud first (`gpt-oss-20b` for questions and scoring, `whisper-large-v3-turbo` for speech; several keys back each other up). Local fallback: Ollama `qwen3:1.7b` and whisper.cpp. Piper for the spoken voice |
| Off-campus job feed | Careerjet job search API, imported every night at 12 (Internshala and similar sites forbid scraping) |
| PDFs | Apache PDFBox (offer letters, reports, NOC on the college letterhead) |
| Mock test code | Docker sandbox `proveyu-code-runner:1` (no network, read-only, time limit) |
| Mail | SMTP, sent in the background (`@Async`) |

## What runs where

| Service | Runs on | Port | Needed for |
|---|---|---|---|
| Frontend | Node (`ng serve`) | **4300** | All portals (port 4200 is used by another local project) |
| Backend | Java + Maven | **8080** | All APIs and the chat WebSocket |
| MySQL | System MySQL 8 (or `scripts/start-db.sh`, Docker, port 3310) | 3306 | All data |
| Ollama | Docker (`ai-services`) | 11434 | AI interview when Groq is unavailable |
| Whisper | Docker (`ai-services`) | 8178 | Speech-to-text when Groq is unavailable |
| Piper | Docker (`ai-services`) | 5002 | Reading interview questions aloud |
| Code runner | Docker image, started per run | – | Coding questions in mock tests |
| Groq | Internet | – | First choice for the AI interview (`GROQ_API_KEY`) |
| Careerjet | Internet | – | Nightly off-campus job import (`CAREERJET_API_KEY`, `CAREERJET_REFERER`) |

---

## Repository structure

```text
proveyu/
├── Frontend/src/app/
│   ├── core/                 # auth, guards, interceptors, token storage, API helpers
│   ├── shared/               # UI kit: modal, stat card, status badge, tables, toasts, navbar, footer
│   └── features/
│       ├── public/           # website and portal chooser (/login)
│       ├── college/          # college portal
│       ├── student/          # student portal: placements, mock tests, AI interview
│       ├── company/          # Drive Room for company panels
│       ├── admin/            # admin portal
│       ├── offcampus/        # off-campus jobs service (shared by admin, student and college pages)
│       └── support/          # Help & support popup (shared by college and student portals)
├── Backend/src/main/java/com/proveyu/
│   ├── auth/                 # sign-in, e-mail verification, OTP, password reset
│   ├── college/              # placement cell: students, companies, drives, rounds, offers, reports
│   ├── student/              # student portal API
│   │   ├── prep/             # mock tests, code runner, proctoring
│   │   └── interview/        # AI interview, credits, UPI purchases
│   ├── admin/                # dashboard, users, admins, settings, broadcast, audit, payments export
│   ├── offcampus/            # off-campus jobs, nightly Careerjet import, fields and interests, NOC requests and PDF
│   ├── support/              # Help & support requests
│   ├── chat/  notification/  # one-to-one chat (WebSocket) and the notification bell
│   └── shared/               # security, settings, audit, e-mail, storage, errors
├── Backend/runner/           # Dockerfile of the mock test code sandbox
├── ai-services/              # docker-compose for Ollama, Whisper and Piper
├── scripts/                  # run scripts, demo seed data, end-to-end tests, run_all_tests.sh, gen_docs.py
├── docs/COLLEGE_PORTAL.md    # detailed guide: modules, proctoring, admin portal, support, off-campus, NOC
├── RUN_AND_TEST.md           # full runbook and the end-to-end test checklist for every portal
└── finaluseddbschema.sql     # structure of the 54 tables the code uses (Flyway baseline 39)
```

---

## Run it locally

### Prerequisites
Java 17+ (21 recommended), Maven 3.8+, Node.js 20+ with npm, MySQL 8, Docker.

### One-time setup
```bash
# 1. Backend configuration. Fill in the DB password, JWT_SECRET (openssl rand -base64 64), GROQ_API_KEY,
#    CAREERJET_API_KEY + CAREERJET_REFERER and the SMTP settings. Never commit Backend/.env.
cp Backend/.env.example Backend/.env

# 2. Frontend packages
cd Frontend && npm install && cd ..

# 3. Mock test code sandbox
docker build -t proveyu-code-runner:1 Backend/runner

# 4. Local AI services (fallback when Groq fails) and the local model
cd ai-services && docker compose up -d --build && docker compose exec ollama ollama pull qwen3:1.7b && cd ..

# 5. The 20-test mock test ladder (loaded after the demo data, see below)
python3 scripts/mocktests/build.py
```

### Every day
```bash
cd ai-services && docker compose up -d && cd ..   # local AI (if not running)
bash scripts/run-backend.sh                        # backend on :8080, applies Flyway migrations
cd Frontend && npm start -- --port 4300            # frontend on :4300
```

Check that everything is up:
```bash
for p in 3306 8080 4300 11434 8178 5002; do ss -ltn | grep -q ":$p " && echo "$p UP" || echo "$p DOWN"; done
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

### First admin
On a new database, set `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` and `ADMIN_BOOTSTRAP_NAME` in `Backend/.env` and
start the backend once. The account is created only if no admin exists. Remove the password from `.env` afterwards and create
further admins from **Admin portal → Admins**.

### Demo data
```bash
python3 scripts/seed_demo.py            # demo college, team, 50 students, companies, drives
python3 scripts/seed_student_portal.py  # applications, rounds, documents, chat for the student portal
python3 scripts/seed_drive_room_demo.py # a drive ready for the Drive Room
python3 scripts/mocktests/load.py       # uploads and publishes the 20 mock tests
```

Off-campus jobs: with `CAREERJET_API_KEY` set, open **Admin portal → Off-campus jobs → Imported → Import now** for the first import
(after that it runs every night), review the drafts and publish them.

---

## Important settings (`Backend/.env`)

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL / USERNAME / PASSWORD` | MySQL connection |
| `JWT_SECRET`, `JWT_EXPIRATION_MS` | Sign-in tokens (default 24 h) |
| `CORS_ALLOWED_ORIGINS` | Frontend origin(s), e.g. `http://localhost:4300` |
| `GROQ_API_KEY` | Groq cloud AI, one key or several comma-separated (`gsk_a,gsk_b,gsk_c`), used in turn; a key at its rate limit is skipped until it recovers. Without a key the AI interview uses the local models (slower) |
| `AI_LOCAL_MODELS_ENABLED` | `true` (default): local models (Ollama, whisper.cpp, Piper) back up Groq. `false`: Groq only; no local AI service is needed and the browser reads questions aloud. (Older name: `INTERVIEW_CLOUD_FALLBACK_LOCAL`) |
| `INTERVIEW_CLOUD_MAX_PARALLEL` | Groq calls at the same time (default 4; raise for more students) |
| `CAREERJET_API_KEY` | Careerjet publisher key for the nightly off-campus job import. Blank = no import |
| `CAREERJET_REFERER` | The website declared in the Careerjet publisher account (Careerjet checks it), e.g. `https://proveyu.com` |
| `OFFCAMPUS_IMPORT_CRON` | When the import runs (default `0 0 0 * * *`, midnight India time) |
| `SPRING_MAIL_*` | SMTP for verification mails, invites and answers |
| `COLLEGE_REQUIRE_APPROVAL` | `true` in production: new colleges wait for admin approval |
| `APP_DEV_EXPOSE_TOKENS` | **`false` in production.** `true` returns OTPs and verification tokens in API responses (local testing only) |
| `ADMIN_BOOTSTRAP_*` | Creates the first admin on an empty database |
| `SUPPORT_INBOX_EMAIL` | Optional: Help & support requests are also e-mailed here (e.g. `support@proveyu.com`) |
| `APP_FRONTEND_URL` | Base URL used in e-mail links |
| `SERVER_ADDRESS`, `TRUSTED_PROXIES` | Production: `127.0.0.1` for both, so only nginx reaches the backend and the rate limit sees the real client address |
| `SPRINGDOC_ENABLED` | `false` in production: no Swagger UI or API list |

Runtime values (interview price, free interview interval, UPI ID, tab-switch warning limit, ladder unlock %, screenshot
retention, website banner) are changed in **Admin portal → Settings** without a restart.

---

## Features

**College portal:** registration with approval, team roles (TPO, branch-scoped coordinator, viewer), students (single add, Excel
import, document verification, export), companies and contacts, drives with eligibility rules and preview, rounds and results,
interview slots, Drive Room for company panels, offers and offer letters, placement policy (tier upgrade, limits, block after
accept, off-campus policy), announcements, chat, the college's own mock tests with proctoring proof, the students' off-campus
applications and offers, NOC requests (approve with a generated NOC on the letterhead or a signed copy, or reject with a reason),
dashboard, reports and audit log.

**Student portal:** self-activation with roll number, profile synced with the college, eligible drives with reasons, apply and
withdraw, round-by-round tracking, interviews, offers and offer letters, document locker, announcements, chat, a 20-step mock
test ladder (MCQ and coding, proctored with screen share, tab-switch screenshots and auto-submit), AI mock interviews from a
drive's JD or the student's own JD (voice or typed answers, scored report, free and paid credits via UPI), off-campus jobs and
internships filtered by the fields the student picks (software, data, mechanical, ...) with eligibility reasons, an
applied → interviewing → offer tracker and NOC requests.

**Admin portal:** dashboard, colleges (approve, suspend, reactivate), users (search, block, password reset, credits), admins,
payments (verify, export), mock tests, proctoring, off-campus jobs (post with eligibility and field, review the nightly Careerjet
import, bulk publish, responses per job), Help & support inbox, broadcast, settings and audit log.

Both the college and student portals have **Help & support** in the side menu: a mail-style form whose requests and answers
are handled in **Admin portal → Support**.

Details: [docs/COLLEGE_PORTAL.md](docs/COLLEGE_PORTAL.md). Architecture: [Backend/PROVEYU_ARCHITECTURE_DOCS.md](Backend/PROVEYU_ARCHITECTURE_DOCS.md).
Every endpoint: [Backend/PROVEYU_API_SPECIFICATION_DOCS.md](Backend/PROVEYU_API_SPECIFICATION_DOCS.md). Every table:
[Backend/PROVEYU_DATABASE_SCHEMA_DOCS.md](Backend/PROVEYU_DATABASE_SCHEMA_DOCS.md) (both generated by `python3 scripts/gen_docs.py`).

---

## Tests

```bash
cd Backend && mvn test                                  # unit and integration tests (Testcontainers, needs Docker)
python3 scripts/smoke_test.py                           # quick API check, including data isolation between colleges
python3 scripts/e2e_portal_test.py                      # college and student portal, end to end
python3 scripts/e2e_prep_test.py                        # mock test upload, attempt, sandbox, scoring
python3 scripts/e2e_interview_test.py                   # AI interview (Groq)
python3 scripts/e2e_three_colleges_test.py              # three colleges, all portals, admin, three interviews at once
python3 scripts/e2e_support_test.py <run-id>            # Help & support (run id printed by the three-colleges test)
python3 scripts/e2e_off_campus_test.py                  # off-campus jobs: post, eligibility, apply tracking, college view (demo data)
```

The end-to-end scripts expect the backend on `http://localhost:8080` (pass another URL as the first argument) and
`APP_DEV_EXPOSE_TOKENS=true`.

All of them in one go: `bash scripts/run_all_tests.sh`. Full setup, run order, accounts and the manual test checklist for every
portal: [RUN_AND_TEST.md](RUN_AND_TEST.md).

---

## Security

- BCrypt password hashes; sign-in requires a verified e-mail.
- JWT with role checks on every endpoint and route guards in the frontend.
- Every college's data is separated: another college's ids return 404. Students see only their own records.
- Blocking a user ends their session immediately (chat included). Suspending a college stops its placement team.
- Rate limiting: 60 requests/min per IP on sign-in routes, 120/min per user elsewhere.
- Student code runs in Docker with no network, a read-only file system, an unprivileged user and a time limit.
- College and admin actions are written to audit logs.

Deploying to a server: start with the one-page [DEPLOY_CHECKLIST.md](DEPLOY_CHECKLIST.md), then follow [DEPLOY_EC2.md](DEPLOY_EC2.md) (AWS) or [DEPLOY_GCP.md](DEPLOY_GCP.md) (Google Cloud, e2-standard-4 on the free trial) (ready-made nginx, systemd and production `.env` files in
[deploy/](deploy/), and the security checklist). In short: HTTPS with HSTS and a Content-Security-Policy at the reverse proxy, `APP_DEV_EXPOSE_TOKENS=false`,
`COLLEGE_REQUIRE_APPROVAL=true`, a long random `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` set to the real domain.
