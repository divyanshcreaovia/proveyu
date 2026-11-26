# ProveYu go-live checklist

Read this first, then follow it top to bottom. It points to the detailed guides for the exact commands:

- **Google Cloud VM:** [DEPLOY_GCP.md](DEPLOY_GCP.md)
- **AWS EC2:** [DEPLOY_EC2.md](DEPLOY_EC2.md)

Both guides set up the same thing on one Ubuntu server:

```text
Internet ──443──► nginx ──► /var/www/proveyu        (Angular app, static files)
                    └─────► 127.0.0.1:8080 backend   (Spring Boot jar, systemd)
                                ├──► 127.0.0.1:3306 MySQL 8
                                ├──► Judge0 CE on its own VM (code runner, internal network only)
                                └──► Groq, Careerjet, SMTP  (outbound only)
```

Only ports 80 and 443 are open to the internet. MySQL, 8080 and Judge0 are never exposed.

---

## 1. What is final (use exactly these)

| What | File / value | Notes |
|---|---|---|
| Code | the latest commit on the branch you were given | build from a clean checkout |
| Database, schema only | [finaluseddbschema.sql](finaluseddbschema.sql) | Flyway version **49**, 66 tables, no data (the platform admin is made by `ADMIN_BOOTSTRAP_*` on the first start) |
| Database, schema + showcase data | [25nov/finaluseddbschema.sql](25nov/finaluseddbschema.sql) | the same 66 tables (Flyway version **49**) with the 20 mock tests and one complete college (LTCE); use it only to show the product |
| Flyway baseline | `SPRING_FLYWAY_BASELINE_VERSION=49` | must equal the "Flyway version" written at the top of the file you loaded |
| Backend settings | [deploy/backend.env.production.example](deploy/backend.env.production.example) → `/etc/proveyu/backend.env` | fill in every empty value |
| Backend service | [deploy/proveyu-backend.service](deploy/proveyu-backend.service) → `/etc/systemd/system/` | runs the jar as user `proveyu` |
| Web server | [deploy/nginx-proveyu.conf](deploy/nginx-proveyu.conf) → `/etc/nginx/sites-available/proveyu` | replace `proveyu.example.com` with the real domain |
| Code runner image | `Backend/runner/Dockerfile` → image **`proveyu-code-runner:1`** | the backend uses this exact name |
| Mock tests (20) | `scripts/mocktests/out/*.json`, loaded with `scripts/mocktests/load.py` | the schema has no data, so load them once |

Do not use `Backend/.env` or `Backend/src/main/resources/application-dev.yml` on the server. They are for local development.

## 2. Collect before you start

Get these from the owner (send them privately, never in git or chat groups):

- [ ] **Domain** and access to its DNS (an A record must point to the server IP).
- [ ] **SMTP** for `info@proveyu.com`: host, port 465, username, password. All mail is sent from this address.
- [ ] The **`support@proveyu.com` mailbox exists.** Mail footers, the Help popup and the Pricing page show it.
- [ ] **3 Groq API keys**, comma-separated (`key1,key2,key3`). These run the AI interviews.
- [ ] **Careerjet API key.** The website registered in the Careerjet publisher account must be the live domain.
- [ ] **Admin e-mail** for the first platform admin, plus a new strong password (not `Admin@1234`).
- [ ] Optional: **UPI ID** for paid AI interviews. It can also be set later in Admin → Settings.

Create these yourself on the server (new values, never the development ones):

- [ ] MySQL app user password: long and random.
- [ ] `JWT_SECRET`: from `openssl rand -base64 64`.

## 3. Server

- [ ] Ubuntu 24.04, at least 4 vCPU / 16 GB / 60 GB disk (GCP `e2-standard-4` or a similar EC2 size).
- [ ] Firewall: 80 and 443 open to everyone; 22 only from your IP or Google IAP; nothing else.
- [ ] Software installed: `nginx certbot mysql-server openjdk-21-jre-headless unattended-upgrades` (no Docker needed on this server).
- [ ] Automatic security updates on; SSH with a key only.
- [ ] User `proveyu` created. Coding questions use the Judge0 VM, so this user needs no extra group.
- [ ] Folders created: `/opt/proveyu`, `/etc/proveyu`, `/var/lib/proveyu/uploads`, `/var/www/proveyu`.

## 4. Database

- [ ] MySQL listens on `127.0.0.1` only (the default).
- [ ] `CREATE DATABASE proveyufinaldb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`
- [ ] App user `proveyu_app@127.0.0.1` with rights **on `proveyufinaldb` only**. Never use root.
- [ ] Load the schema **into the empty database**, once: `mysql proveyufinaldb < finaluseddbschema.sql` (or `25nov/finaluseddbschema.sql` for the showcase data)
- [ ] `SPRING_FLYWAY_BASELINE_VERSION=49` is in `/etc/proveyu/backend.env`.

How Flyway works here: the dump has no Flyway history, so on the first start Flyway records the database as version 49 and runs
nothing old. On every later start it runs only new files (`V50__...` and later) from `Backend/src/main/resources/db/migration`.
The baseline matters only that first time; once `flyway_schema_history` exists it is ignored.

A database that already has data and was made from an older dump (for example the first 25 Nov dump, version 44) is **not reloaded**:
set the baseline to the version that dump had (`44`) and start the backend; it applies V45 onwards by itself. Take a backup first (MySQL does
not undo a migration that fails half way). If `SHOW TABLES LIKE 'flyway_schema_history'` already finds the table, change nothing.

**Never:**
- load the schema into a database that already has tables
- start the backend on a loaded dump without the baseline, or with a baseline higher than the dump's real version (it then tries V1 onwards and fails at V15, or skips migrations and the new tables are missing)
- edit a `V__` file that already ran

## 5. Build

On a machine with JDK 17+ (21 works), Maven and Node 20+:

```bash
cd Backend && mvn -DskipTests package && cd ..        # Backend/target/proveyu-backend-*.jar
cd Frontend && npm ci && npx ng build && cd ..         # Frontend/dist/proveyu-frontend/browser/
```

- [ ] The jar is copied to `/opt/proveyu/proveyu-backend.jar`.
- [ ] The **contents** of `Frontend/dist/proveyu-frontend/browser/` are copied into `/var/www/proveyu/`.

The production frontend calls the API on the same domain (`/api/...`) through nginx, so nothing in the frontend needs changing.

## 6. Code runner: Judge0 (mock-test coding questions)

Judge0 CE runs on its own VM (the Judge0 instance from the code runner setup, Ubuntu 22.04). The backend reaches it over the
internal network only. Nothing to build on this server.

- [ ] `JUDGE0_URL=http://<Judge0 VM internal IP>:2358` in `/etc/proveyu/backend.env` (the internal IP, never the public one).
- [ ] `JUDGE0_TOKEN` set to the same token the Judge0 VM requires (`X-Auth-Token`). It is never logged.
- [ ] The Judge0 VM's firewall allows port 2358 only from this server's internal address.
- [ ] Check after start: `GET /api/v1/admin/code-runner/health` as admin, or the `[CODE RUNNER] ... ready` line in the backend log.
- [ ] Language ids in `application.yml` (`code.execution.languages`) match the VM's `GET /languages`.
- [ ] For a class using Code ground (practice problems), set `JUDGE0_MAX_PARALLEL=10`, `CODE_QUEUE_WAIT_MS=90000` and `JUDGE0_POLL_TIMEOUT_MS=60000`, and give the Judge0 VM enough workers (`COUNT` in `judge0.conf`). Measured numbers are in [docs/CODE_GROUND.md](docs/CODE_GROUND.md). The two timeouts also apply to mock tests.

## 7. Backend settings: `/etc/proveyu/backend.env`

Start from [deploy/backend.env.production.example](deploy/backend.env.production.example). Then `chown proveyu:proveyu` and `chmod 600`.

**These must be exactly this way** (security):

| Setting | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (never `dev`) |
| `APP_DEV_EXPOSE_TOKENS` | `false` |
| `SPRINGDOC_ENABLED` | `false` |
| `COLLEGE_REQUIRE_APPROVAL` | `true` |
| `SERVER_ADDRESS` | `127.0.0.1` |
| `TRUSTED_PROXIES` | `127.0.0.1` |
| `FORWARD_HEADERS_STRATEGY` | `native` |

**Fill these in:**

| Setting | Value |
|---|---|
| `CORS_ALLOWED_ORIGINS`, `APP_FRONTEND_URL`, `CAREERJET_REFERER` | `https://<domain>` (mail links use `APP_FRONTEND_URL`) |
| `SPRING_DATASOURCE_PASSWORD` | the MySQL app user password |
| `JWT_SECRET` | the new random secret |
| `SPRING_FLYWAY_BASELINE_VERSION` | `49` (the version at the top of the dump you loaded) |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` | first admin; **delete the password line after the first start** |
| `SPRING_MAIL_*` | SMTP for `info@proveyu.com` |
| `SUPPORT_INBOX_EMAIL` | `support@proveyu.com` (support tickets and pricing enquiries are e-mailed here) |
| `GROQ_API_KEY` | the 3 keys, comma-separated |
| `AI_LOCAL_MODELS_ENABLED` | `false` (Groq only; questions are read by the browser's voice) |
| `CAREERJET_API_KEY` | the key |
| Upload folders (`*_UPLOAD_DIR`, `INTERVIEW_AUDIO_DIR`, `PREP_PROCTOR_DIR`) | keep the `/var/lib/proveyu/uploads/...` values from the template |

## 8. Start, HTTPS, nginx

- [ ] `sudo systemctl enable --now proveyu-backend`, then `journalctl -u proveyu-backend -f` until "Started".
- [ ] The log shows `Successfully baselined schema with version: 49` and `No migration necessary`, and no errors.
- [ ] Optional check: `SELECT version, description FROM flyway_schema_history ORDER BY installed_rank;` ends with `49 password changed at`, and `SHOW TABLES LIKE 'code_ground%';` lists 8 tables.
- [ ] Remove `ADMIN_BOOTSTRAP_PASSWORD` from the env file, then `sudo systemctl restart proveyu-backend`.
- [ ] DNS A record → server IP.
- [ ] Certificate with certbot (standalone, with the stop/start hooks in the guide), then `sudo certbot renew --dry-run` works.
- [ ] nginx config installed, domain replaced, default site removed, `sudo nginx -t` OK.

## 9. Data that comes with the schema

`25nov/finaluseddbschema.sql` (not the schema-only file in the repository root) already holds the 20 mock tests and one complete college for showing the product:
Lokmanya Tilak College of Engineering (`ltce.in`), with its team, 60 students, drives in every stage, offers, a scheduled
exam, off-campus jobs and notifications. The college passwords are not in the repository; the owner has them.

- [ ] Admin → Mock tests shows 20 published tests; Admin → Colleges shows LTCE as approved.
- [ ] Before real students of LTCE use the platform, remove the showcase students and drives (they use `@ltce.in` addresses:
      keep mail in mind, see below), or load a database without them.
- [ ] Only if you built the database without this file: load the mock tests with
      `ADMIN_EMAIL=<admin e-mail> ADMIN_PASSWORD=<admin password> python3 scripts/mocktests/load.py https://<domain>`.

**Mail:** every showcase account has an `@ltce.in` address. With SMTP on, actions such as publishing a drive or an exam e-mail
those addresses. Do not publish or change showcase drives / exams with mail on unless those mailboxes are yours.

**Never** run the other `seed_*` scripts or the `e2e_*` / `run_all_tests.sh` test suites against production. They create demo
accounts with public passwords.

## 10. Check before announcing

Security (from your laptop):

```bash
curl -sI https://<domain> | grep -iE "strict-transport|content-security|x-frame"     # all three present
curl -s -o /dev/null -w "%{http_code}\n" https://<domain>/swagger-ui.html            # 404
curl -s -o /dev/null -w "%{http_code}\n" https://<domain>/actuator/env               # 404
nc -zv -w 3 <server IP> 3306 8080                                                    # both fail
```

By hand in the browser:

- [ ] `https://<domain>` opens over HTTPS. Header links work, including **Pricing**.
- [ ] `/admin/login` works with the new admin. The admin login is not linked on the public Sign in page, so open the URL directly.
- [ ] Send a test enquiry on `/pricing`: it appears in Admin → Pricing enquiries, and a mail arrives at `support@proveyu.com`.
      Then close it.
- [ ] Register a test college, approve it in Admin → Colleges, and add one student.
- [ ] The student activates the account: the OTP mail arrives, from `info@proveyu.com`, and links open the live domain.
- [ ] The student opens a mock test with a coding question and runs code. This proves the code runner works.
- [ ] The student starts an AI interview: questions are spoken and the report comes after.
- [ ] Admin → Off-campus jobs → **Import now** pulls Careerjet jobs into the Imported tab (otherwise it runs every night at 12).
- [ ] Delete or suspend the test college afterwards.

## 11. After go-live

- [ ] Nightly database dump and a copy of `/var/lib/proveyu/uploads` (resumes, offer letters, NOC PDFs) to private storage.
      Commands are in the guides.
- [ ] Disk snapshots on.
- [ ] Logs: `journalctl -u proveyu-backend` (backend), `/var/log/nginx/` (requests).
- [ ] For big drives where a whole lab shares one IP, raise `AUTH_LIMIT_PER_MINUTE` (default 60 sign-ins a minute per IP).

## 12. Updating later

```bash
# build as in step 5 and copy the new files to the server, then:
sudo systemctl stop proveyu-backend
sudo cp proveyu-backend-*.jar /opt/proveyu/proveyu-backend.jar
sudo rm -rf /var/www/proveyu/* && sudo cp -r browser/* /var/www/proveyu/
sudo systemctl start proveyu-backend        # Flyway runs any new migration by itself
```

- No SQL by hand. Take a database dump before every update.
- Judge0 needs no change on a backend update.
- Users should hard-refresh (Ctrl+Shift+R) once after a frontend update.
