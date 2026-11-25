# Deploy ProveYu on Google Cloud (e2-standard-4, free trial)

One Compute Engine VM, **e2-standard-4 (4 vCPU, 16 GB)** in **Mumbai (`asia-south1`)**, runs everything: nginx (HTTPS, web app,
reverse proxy), the backend, MySQL and, optionally, the local AI backup (Ollama, Whisper, Piper, which use Docker). Mock-test code runs on a separate Judge0 VM (DEPLOY_CHECKLIST.md, section 6). Only
ports 80 and 443 are open to the internet; you reach the VM over SSH through Google's IAP tunnel. The nginx, systemd and production
`.env` files are in [deploy/](deploy/); the security checklist in [DEPLOY_EC2.md](DEPLOY_EC2.md#1-before-you-start-security-checklist)
applies here unchanged.

**Cost (estimate, check the [pricing calculator](https://cloud.google.com/products/calculator) for `asia-south1`):** about
$115–125 a month for the VM, a 60 GB balanced disk and a static IP, so the $300 free-trial credit lasts about 2.5 months. The trial
credit expires after 90 days even if some is left.

Run the commands on your machine with the [gcloud CLI](https://cloud.google.com/sdk/docs/install) installed, or in **Cloud Shell**
(the `>_` icon in the console, gcloud is already there). Replace `proveyu.example.com` with your domain everywhere.

---

## 1. Project, billing and APIs

```bash
gcloud auth login
gcloud projects create proveyu-prod-<something-unique> --name="ProveYu"
gcloud config set project proveyu-prod-<something-unique>

gcloud billing accounts list                                    # copy the ACCOUNT_ID of the free-trial billing account
gcloud billing projects link proveyu-prod-<something-unique> --billing-account=<ACCOUNT_ID>

gcloud services enable compute.googleapis.com iap.googleapis.com billingbudgets.googleapis.com
gcloud config set compute/region asia-south1
gcloud config set compute/zone asia-south1-a
```

## 2. Budget alert (so the credit never surprises you)

```bash
gcloud billing budgets create --billing-account=<ACCOUNT_ID> --display-name="ProveYu trial" \
  --budget-amount=300USD --threshold-rule=percent=0.33 --threshold-rule=percent=0.66 --threshold-rule=percent=0.9
```

You get an e-mail at about $100, $200 and $270 spent (Billing → Budgets & alerts shows it too).

## 3. Network: static IP and firewall

```bash
gcloud compute addresses create proveyu-ip --region=asia-south1
gcloud compute addresses describe proveyu-ip --region=asia-south1 --format="value(address)"   # note this IP

# web traffic to the VM only
gcloud compute firewall-rules create proveyu-allow-web --network=default --direction=INGRESS \
  --allow=tcp:80,tcp:443 --source-ranges=0.0.0.0/0 --target-tags=proveyu-web
# SSH only through Google's IAP tunnel (this range is Google's IAP), never from the whole internet
gcloud compute firewall-rules create proveyu-allow-iap-ssh --network=default --direction=INGRESS \
  --allow=tcp:22 --source-ranges=35.235.240.0/20 --target-tags=proveyu-web
# the default network opens SSH and RDP to everyone: remove those rules
gcloud compute firewall-rules delete default-allow-ssh default-allow-rdp --quiet
```

MySQL (3306), the backend (8080) and the AI services are never in a firewall rule: they stay unreachable from outside.

## 4. The VM

```bash
gcloud compute instances create proveyu \
  --zone=asia-south1-a --machine-type=e2-standard-4 \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=60GB --boot-disk-type=pd-balanced \
  --address=proveyu-ip --tags=proveyu-web \
  --shielded-secure-boot --shielded-vtpm --shielded-integrity-monitoring \
  --metadata=enable-oslogin=TRUE
```

Point the domain at it: at your DNS provider add an **A record** `proveyu.example.com → <the static IP>`.

Connect:

```bash
gcloud compute ssh proveyu --zone=asia-south1-a --tunnel-through-iap
```

## 5. Install the software (on the VM)

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y nginx certbot python3-certbot-nginx mysql-server openjdk-21-jre-headless \
                    docker.io docker-compose-v2 unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades          # automatic security updates

sudo useradd --system --create-home --shell /usr/sbin/nologin proveyu
sudo mkdir -p /opt/proveyu /etc/proveyu /var/lib/proveyu/uploads /var/www/proveyu
sudo chown -R proveyu:proveyu /opt/proveyu /var/lib/proveyu
```

## 6. Database (on the VM)

MySQL listens only on localhost by default; keep it that way.

```bash
sudo mysql
```
```sql
CREATE DATABASE proveyufinaldb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'proveyu_app'@'127.0.0.1' IDENTIFIED BY '<long random password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON proveyufinaldb.* TO 'proveyu_app'@'127.0.0.1';
EXIT;
```

## 7. Build on your machine and copy to the VM

```bash
# on your machine, in the project folder
cd Backend && mvn -DskipTests package && cd ..
cd Frontend && npm ci && npx ng build && cd ..
tar czf proveyu-release.tgz Backend/target/proveyu-backend-*.jar Frontend/dist/proveyu-frontend/browser \
    Backend/runner ai-services deploy finaluseddbschema.sql

gcloud compute scp proveyu-release.tgz proveyu:~ --zone=asia-south1-a --tunnel-through-iap
```

On the VM:

```bash
mkdir -p ~/release && tar xzf ~/proveyu-release.tgz -C ~/release && cd ~/release

sudo cp Backend/target/proveyu-backend-*.jar /opt/proveyu/proveyu-backend.jar
sudo cp -r Frontend/dist/proveyu-frontend/browser/* /var/www/proveyu/
# mock-test code: set JUDGE0_URL (Judge0 VM internal IP) and JUDGE0_TOKEN in /etc/proveyu/backend.env (DEPLOY_CHECKLIST.md, section 6)

sudo mysql proveyufinaldb < finaluseddbschema.sql                  # the schema; first line names its Flyway version
head -5 finaluseddbschema.sql | grep -o "Flyway version [0-9]*"   # 44 -> SPRING_FLYWAY_BASELINE_VERSION=44
```

## 8. Optional: local AI backup (16 GB has room for it)

Only if `AI_LOCAL_MODELS_ENABLED=true`. With Groq keys alone you can skip this step.

```bash
cd ~/release/ai-services && sudo docker compose up -d --build
sudo docker compose exec ollama ollama pull qwen3:1.7b
```

The compose file binds Ollama, Whisper and Piper to 127.0.0.1 only.

## 9. Backend settings and service

```bash
sudo cp ~/release/deploy/backend.env.production.example /etc/proveyu/backend.env
sudo nano /etc/proveyu/backend.env
```

Fill in (see the comments in the file): the domain in `CORS_ALLOWED_ORIGINS`, `APP_FRONTEND_URL` and `CAREERJET_REFERER`; the DB
password from step 6; a new `JWT_SECRET` (`openssl rand -base64 64`); a new admin e-mail and strong password; SMTP; Groq keys;
`AI_LOCAL_MODELS_ENABLED` (true only if you did step 8); and add `SPRING_FLYWAY_BASELINE_VERSION=<the version from step 7>`.

```bash
sudo chown proveyu:proveyu /etc/proveyu/backend.env && sudo chmod 600 /etc/proveyu/backend.env
sudo cp ~/release/deploy/proveyu-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now proveyu-backend
sudo journalctl -u proveyu-backend -f          # wait for "Started ProveyuApplication", then Ctrl+C
```

After the first start, remove `ADMIN_BOOTSTRAP_PASSWORD` from the file and restart: `sudo systemctl restart proveyu-backend`.

## 10. nginx and HTTPS

```bash
sudo cp ~/release/deploy/nginx-proveyu.conf /etc/nginx/sites-available/proveyu
sudo sed -i 's/proveyu.example.com/<your domain>/g' /etc/nginx/sites-available/proveyu
sudo ln -s /etc/nginx/sites-available/proveyu /etc/nginx/sites-enabled/ && sudo rm -f /etc/nginx/sites-enabled/default
```

The config points at the certificate, so get the certificate first (certbot briefly uses port 80 itself, so nginx is stopped):

```bash
sudo systemctl stop nginx
sudo certbot certonly --standalone -d <your domain> --agree-tos -m <your e-mail> \
     --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
sudo nginx -t && sudo systemctl start nginx
```

The two hooks are saved, so the automatic renewal (every 60–90 days) stops and starts nginx by itself. Test it once:
`sudo certbot renew --dry-run`.

## 11. Check it

From your machine:

```bash
curl -sI https://<your domain> | grep -iE "strict-transport|content-security|x-frame"         # all three present
curl -s -o /dev/null -w "%{http_code}\n" https://<your domain>/swagger-ui.html                # 404
curl -s -o /dev/null -w "%{http_code}\n" https://<your domain>/actuator/env                   # 404
nc -zv -w 3 <static IP> 22 3306 8080 11434                                                    # all fail
```

Then open `https://<your domain>/admin/login` with the admin from step 9, approve a test college, and run through
[RUN_AND_TEST.md section 7](RUN_AND_TEST.md#7-manual-end-to-end-scenarios) by hand (not the automated suites: they create test data).

## 12. Backups

```bash
# a private, versioned bucket in Mumbai
gcloud storage buckets create gs://proveyu-backups-<unique> --location=asia-south1 --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://proveyu-backups-<unique> --versioning

# daily disk snapshots, kept 7 days
gcloud compute resource-policies create snapshot-schedule proveyu-daily --region=asia-south1 \
  --daily-schedule --start-time=20:30 --max-retention-days=7
gcloud compute disks add-resource-policies proveyu --zone=asia-south1-a --resource-policies=proveyu-daily
```

On the VM, a nightly database dump to the bucket (`sudo crontab -e`):

```cron
0 2 * * * mysqldump --single-transaction proveyufinaldb | gzip > /var/lib/proveyu/db-$(date +\%F).sql.gz && gcloud storage cp /var/lib/proveyu/db-$(date +\%F).sql.gz gs://proveyu-backups-<unique>/ && find /var/lib/proveyu -name 'db-*.sql.gz' -mtime +3 -delete
```

(The VM's service account needs *Storage Object Creator* on that bucket: IAM → the bucket → Grant access.)

## 13. Save credit

- **Stop the VM when nobody uses it** (nights, weekends): `gcloud compute instances stop proveyu --zone=asia-south1-a`, and
  `start` to bring it back. A stopped VM costs only its disk and static IP (a few dollars a month); the IP stays the same.
- Watch spending in Billing → Reports. Before the trial ends (90 days or $300), take a final database dump; upgrade the billing
  account if the platform should keep running, otherwise the VM is stopped and later deleted.

## 14. Updating to a new version

```bash
# your machine: build and copy as in step 7, then on the VM:
sudo systemctl stop proveyu-backend
sudo cp ~/release/Backend/target/proveyu-backend-*.jar /opt/proveyu/proveyu-backend.jar
sudo rm -rf /var/www/proveyu/* && sudo cp -r ~/release/Frontend/dist/proveyu-frontend/browser/* /var/www/proveyu/
sudo systemctl start proveyu-backend      # Flyway applies any new migration on start
```
