# Deploy ProveYu on one EC2 server, securely

One Ubuntu EC2 instance runs everything: nginx (HTTPS, the web app, the reverse proxy), the Spring Boot backend, MySQL, and Docker
for the mock-test code runner. Only nginx is reachable from the internet. Ready-made files are in [deploy/](deploy/). On Google Cloud instead, follow [DEPLOY_GCP.md](DEPLOY_GCP.md).

```text
Internet ──443──► nginx ──► /var/www/proveyu (Angular app)
                    └──────► 127.0.0.1:8080 backend ──► 127.0.0.1:3306 MySQL
                                                   ├──► Docker (code runner, no network)
                                                   └──► Groq, Careerjet, SMTP (outbound only)
```

## 1. Before you start: security checklist

Tick every line before the platform goes live. Each one was checked in the security review of 19 Nov 2026.

| # | Item | Why |
|---|---|---|
| 1 | `SPRING_PROFILES_ACTIVE=prod` (never `dev`) | the dev profile returns OTPs and invite tokens in API responses |
| 2 | `APP_DEV_EXPOSE_TOKENS=false`, `COLLEGE_REQUIRE_APPROVAL=true` | no codes in responses; new colleges wait for your approval |
| 3 | `SPRINGDOC_ENABLED=false` | the API list and Swagger UI are not served (404) |
| 4 | `SERVER_ADDRESS=127.0.0.1`, `TRUSTED_PROXIES=127.0.0.1`, `FORWARD_HEADERS_STRATEGY=native` | the backend is reachable only through nginx, and the rate limit sees the real client address (a fake `X-Forwarded-For` cannot dodge it) |
| 5 | New `JWT_SECRET` (`openssl rand -base64 64`) | never reuse the development secret |
| 6 | New, strong admin password (not `Admin@1234`, which is in the repository) | the demo passwords are public |
| 7 | New DB user and strong password; the app user has rights on `proveyufinaldb` only; never root | a leak exposes one database, not the server |
| 8 | No demo data on the production database (do not run the `seed_*` scripts or the test suites there) | demo accounts have published passwords |
| 9 | `.env` / `backend.env` mode `600`, owner `proveyu`; never committed | secrets stay on the server |
| 10 | Security group: 443 and 80 from anywhere, 22 from your IP only; nothing else | MySQL, 8080, Ollama, Whisper and Piper are never exposed |
| 11 | SSH with a key only (`PasswordAuthentication no`), automatic security updates on | the usual way servers are broken into |
| 12 | Rotate the SMTP, Groq and Careerjet keys if this repository was ever public or shared | old secrets (a DB password, a JWT secret, Razorpay keys) are in the git history of `application.yml`; they are not the current values, but anything in history must be treated as known |

## 2. Server setup

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y nginx certbot python3-certbot-nginx mysql-server openjdk-21-jre-headless docker.io unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades

sudo useradd --system --create-home --shell /usr/sbin/nologin proveyu
sudo usermod -aG docker proveyu
sudo mkdir -p /opt/proveyu /etc/proveyu /var/lib/proveyu/uploads /var/www/proveyu
sudo chown -R proveyu:proveyu /opt/proveyu /var/lib/proveyu
```

MySQL listens only on localhost by default (`bind-address = 127.0.0.1` in `/etc/mysql/mysql.conf.d/mysqld.cnf`); keep it. Then:

```sql
CREATE DATABASE proveyufinaldb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'proveyu_app'@'127.0.0.1' IDENTIFIED BY '<long random password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON proveyufinaldb.* TO 'proveyu_app'@'127.0.0.1';
```

Load the schema once into the empty database: `mysql proveyufinaldb < finaluseddbschema.sql`, then set `SPRING_FLYWAY_BASELINE_VERSION=42`
(the version written at the top of that file) in the backend env file. On the first start Flyway records the database as version 42 and
runs nothing old; from then on every deploy runs only the new migrations (V43, V44, ...) automatically. Never edit a `V__` file that
already ran, and never run the old migrations on an empty production database (V15 needs data that only exists in dev).

## 3. Build and install

```bash
# on your machine or the server
cd Backend && mvn -DskipTests package && cd ..
cd Frontend && npm ci && npx ng build && cd ..
docker build -t proveyu-code-runner:1 Backend/runner

sudo cp Backend/target/proveyu-backend-*.jar /opt/proveyu/proveyu-backend.jar
sudo cp -r Frontend/dist/proveyu-frontend/browser/* /var/www/proveyu/
sudo cp deploy/backend.env.production.example /etc/proveyu/backend.env   # fill it in
sudo chown proveyu:proveyu /etc/proveyu/backend.env && sudo chmod 600 /etc/proveyu/backend.env

sudo cp deploy/proveyu-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now proveyu-backend

sudo cp deploy/nginx-proveyu.conf /etc/nginx/sites-available/proveyu      # replace proveyu.example.com
sudo ln -s /etc/nginx/sites-available/proveyu /etc/nginx/sites-enabled/ && sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl stop nginx && sudo certbot certonly --standalone -d proveyu.example.com --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx" && sudo nginx -t && sudo systemctl start nginx
```

## 4. Check it after deploying

```bash
curl -sI https://proveyu.example.com | grep -iE "strict-transport|content-security|x-frame"   # headers present
curl -s -o /dev/null -w "%{http_code}\n" https://proveyu.example.com/swagger-ui.html          # 404
curl -s -o /dev/null -w "%{http_code}\n" https://proveyu.example.com/actuator/env             # 404
nc -zv <public-ip> 8080 3306 11434                                                           # all refused / timed out
curl -s -X POST https://proveyu.example.com/api/v1/auth/forgot-password -H 'Content-Type: application/json' \
     -d '{"email":"someone@example.com"}' | grep -c devOtp                                    # 0
```

## 5. Known limits (accepted for now)

- **Code runner on the same server.** The backend starts Docker containers, so the `proveyu` user is in the `docker` group, which is
  as powerful as root. Student code itself runs with no network, a read-only file system, no Linux capabilities, an unprivileged
  user, 256 MB RAM, 1 CPU, 64 processes and a time limit. If the backend itself were compromised, the attacker would get the
  server. For large drives move the runner to its own instance (see the scaling notes in the architecture doc).
- **Tokens in the browser.** The sign-in token is kept in browser storage, the normal pattern for this kind of app. The
  Content-Security-Policy from nginx blocks injected scripts, and Angular escapes all data it displays.
- **One address, many students.** A college lab often shares one public address; 60 sign-ins a minute per address apply.
  Raise `AUTH_LIMIT_PER_MINUTE` for big drives.
- **Angular router advisory** (GHSA-ff3f-86qr-9cv3 and two others) affects server-side rendering only; ProveYu renders in the
  browser. Update all `@angular/*` packages together with `ng update` when convenient.

## 6. Backups and monitoring

- Nightly `mysqldump proveyufinaldb | gzip` and a copy of `/var/lib/proveyu/uploads` to S3 (encrypted bucket, versioning on).
- EBS snapshots of the data volume.
- `journalctl -u proveyu-backend` for the backend log; `/var/log/nginx/proveyu.access.log` for requests (the WebSocket token is
  kept out of it by the log format).
