# ProveYu go-live checklist

Read this first, then follow it top to bottom. It points to the detailed guides for the exact commands:

- **Google Cloud VM:** [DEPLOY_GCP.md](DEPLOY_GCP.md)
- **AWS EC2:** [DEPLOY_EC2.md](DEPLOY_EC2.md)

Both guides set up the same thing on one Ubuntu server:

```text
Internet ──443──► nginx ──► /var/www/proveyu        (Angular app, static files)
                    └─────► 127.0.0.1:8080 backend   (Spring Boot jar, systemd)
                                ├──► 127.0.0.1:3306 MySQL 8
                                ├──► Docker: code runner sandbox (no network)
                                └──► Groq, Careerjet, SMTP  (outbound only)
```

Only ports 80 and 443 are open to the internet. MySQL, 8080 and Docker are never exposed.

---

## 1. What is final (use exactly these)

| What | File / value | Notes |
|---|---|---|
| Code | the latest commit on the branch you were given | build from a clean checkout |
| Database schema | [finaluseddbschema.sql](finaluseddbschema.sql) | Flyway version **39**, 54 tables, structure only |
| Flyway baseline | `SPRING_FLYWAY_BASELINE_VERSION=39` | must equal the version on line 4 of the schema file |
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
- [ ] Software installed: `nginx certbot mysql-server openjdk-21-jre-headless docker.io unattended-upgrades`.
- [ ] Automatic security updates on; SSH with a key only.
- [ ] User `proveyu` created **and added to the `docker` group**. Without the group, coding questions fail with "The code runner is not available".
- [ ] Folders created: `/opt/proveyu`, `/etc/proveyu`, `/var/lib/proveyu/uploads`, `/var/www/proveyu`.

## 4. Database

- [ ] MySQL listens on `127.0.0.1` only (the default).
- [ ] `CREATE DATABASE proveyufinaldb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`
- [ ] App user `proveyu_app@127.0.0.1` with rights **on `proveyufinaldb` only**. Never use root.
- [ ] Load the schema **into the empty database**, once: `mysql proveyufinaldb < finaluseddbschema.sql`
- [ ] `SPRING_FLYWAY_BASELINE_VERSION=39` is in `/etc/proveyu/backend.env`.

How Flyway works here: on the first start it records the database as version 39 and runs nothing old. On every later start it runs
only new files (`V40__...`, `V41__...`) from `Backend/src/main/resources/db/migration`.

**Never:**
- load the schema into a database that already has tables
- start the backend on an empty database without the baseline (it then tries V1 onwards and fails at V15)
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

## 6. Code runner sandbox (mock-test coding questions)

On the server:

```bash
sudo docker build -t proveyu-code-runner:1 Backend/runner
sudo docker images | grep proveyu-code-runner          # must show tag 1
```

- [ ] The image is built with exactly the name `proveyu-code-runner:1`.
- [ ] User `proveyu` is in the `docker` group, and the service file has `SupplementaryGroups=docker`.
- [ ] Nothing else is needed. The backend starts one short-lived container per run with no network, a read-only file system,
      256 MB RAM, 1 CPU and a time limit. 4 runs at a time; more wait in a queue.

The image installs Python, Node, Java 21 and g++ (Alpine). Rebuild it only if `Backend/runner/Dockerfile` changes.

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
| `SPRING_FLYWAY_BASELINE_VERSION` | `39` |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` | first admin; **delete the password line after the first start** |
| `SPRING_MAIL_*` | SMTP for `info@proveyu.com` |
| `SUPPORT_INBOX_EMAIL` | `support@proveyu.com` (support tickets and pricing enquiries are e-mailed here) |
| `GROQ_API_KEY` | the 3 keys, comma-separated |
| `AI_LOCAL_MODELS_ENABLED` | `false` (Groq only; questions are read by the browser's voice) |
| `CAREERJET_API_KEY` | the key |
| Upload folders (`*_UPLOAD_DIR`, `INTERVIEW_AUDIO_DIR`, `PREP_PROCTOR_DIR`) | keep the `/var/lib/proveyu/uploads/...` values from the template |

## 8. Start, HTTPS, nginx

- [ ] `sudo systemctl enable --now proveyu-backend`, then `journalctl -u proveyu-backend -f` until "Started".
- [ ] The log shows Flyway baseline 39 and no errors.
- [ ] Remove `ADMIN_BOOTSTRAP_PASSWORD` from the env file, then `sudo systemctl restart proveyu-backend`.
- [ ] DNS A record → server IP.
- [ ] Certificate with certbot (standalone, with the stop/start hooks in the guide), then `sudo certbot renew --dry-run` works.
- [ ] nginx config installed, domain replaced, default site removed, `sudo nginx -t` OK.

## 9. Load the mock tests (once)

From any machine with Python 3 and the repository:

```bash
ADMIN_EMAIL=<admin e-mail> ADMIN_PASSWORD=<admin password> python3 scripts/mocktests/load.py https://<domain>
```

- [ ] Admin → Mock tests shows 20 published tests.

**Never** run `seed_*` scripts or the `e2e_*` / `run_all_tests.sh` test suites against production. They create demo accounts with public passwords.

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
sudo systemctl start proveyu-backend        # Flyway runs any new V40+ migration by itself
```

- No SQL by hand. Take a database dump before every update.
- Rebuild the runner image only if `Backend/runner/Dockerfile` changed.
- Users should hard-refresh (Ctrl+Shift+R) once after a frontend update.
