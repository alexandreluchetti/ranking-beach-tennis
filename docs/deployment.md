# Deployment (single VPS with Docker Compose)

Everything runs on one Linux server from `docker-compose.yml` with the `prod` profile:

| Service | Role | Exposed |
|---|---|---|
| `caddy` | HTTPS reverse proxy, automatic Let's Encrypt certificates | 80, 443 |
| `backend` | Spring Boot API (built from `Dockerfile`) | to `caddy`, and on the server loopback (`127.0.0.1:8080`) |
| `postgres` | PostgreSQL 17, data in the `postgres-data` volume | server loopback only |
| `db-backup` | Daily `pg_dump` into `./backups`, kept `BACKUP_KEEP_DAYS` days | — |

## Requirements

- A VPS (Hostinger KVM plan or similar), Ubuntu LTS, **2 GB RAM or more**, Docker Engine with the
  Compose plugin. Shared hosting cannot run this.
- A domain or subdomain (e.g. `api.example.com.br`) with a DNS **A record** pointing to the VPS IP.
- Ports 80 and 443 open to the internet; SSH restricted to key authentication.

## Secrets and configuration

All configuration lives in `.env` on the server (copy `.env.example`). Never commit it.

| Item | Sensitive | Notes |
|---|---|---|
| `POSTGRES_PASSWORD` | **Secret** | Required. Generate with `openssl rand -base64 32`. Set it before the first start: PostgreSQL stores it when the volume is created. |
| `./secrets/firebase-service-account.json` | **Secret** | Only for push (`PUSH_FCM_ENABLED=true`). File permissions `600`; `secrets/` is git-ignored. |
| `ADMIN_AUTH_SUBJECT` | Restricted | Firebase UID of the administrator. Not a credential, but it decides who is admin. |
| `DOMAIN` | No | Public name of the API. |
| `FIREBASE_PROJECT_ID` | No | `ranking-beach-tennis`. |
| `API_DOCS_ENABLED` | No | Keep `false` in production. |
| `CORS_ALLOWED_ORIGINS` | No | Web app origins allowed to call `/api/**` from a browser, comma-separated and exact (`https://ranking-beach-tennis.web.app,https://ranking-beach-tennis.firebaseapp.com`). Empty blocks all browser cross-origin calls; the mobile apps are not affected. |

## Current production

| Item | Value |
|---|---|
| API base URL | `https://api.ranking-beach-tennis.tech` |
| Provider | Hostinger VPS (KVM), Ubuntu 24.04 LTS |
| Server IP | `179.236.77.80` |
| DNS | Hostinger: `A api → 179.236.77.80`, TTL 300 |
| Web app origins (CORS) | `https://ranking-beach-tennis.web.app`, `https://ranking-beach-tennis.firebaseapp.com` |
| Repository on the server | `/home/deploy/ranking-beach-tennis` (commands run as `deploy`, see below) |

## Security rules (mandatory)

The server was taken over on 2026-11-24 through an SSH password login (see "Incident history").
These rules are not optional:

- SSH accepts **keys only**, for every user. Password and keyboard-interactive logins are off,
  checked with `sshd -T` (Ubuntu's cloud-init drop-in turns passwords back on otherwise).
- `deploy` has **no password** (`--disabled-password`) and no `sudo`; it runs Docker only. Being
  in the `docker` group is equivalent to root, which is why its login must be key-only.
- `root` logs in with a key only (`PermitRootLogin prohibit-password`); the hPanel browser
  terminal keeps working because it uses a Hostinger-managed key.
- `ufw` allows only 22, 80 and 443; `fail2ban` bans repeated SSH failures; `unattended-upgrades`
  installs security updates.
- The hPanel account has a strong password and two-factor authentication.

## Server setup (Hostinger)

1. **VPS** (hPanel → VPS): KVM plan (KVM 1 is enough), Ubuntu 24.04 LTS, a strong root password
   (kept only in your password manager; SSH never accepts it) and your SSH public key
   (`ssh-keygen -t ed25519` on your computer). Shared hosting cannot run Docker.
2. **Domain and DNS** (hPanel → Domains → the domain → DNS / Nameservers): the domain must be
   registered and active (finish the contact data and the ICANN e-mail verification). Add an
   `A` record `api` → VPS IP. Check before starting Caddy:
   `nslookup api.ranking-beach-tennis.tech 8.8.8.8` must return the VPS IP. Starting Caddy
   before DNS resolves makes certificate issuance fail, and Let's Encrypt rate-limits retries.
   CAA records, if any, must allow `letsencrypt.org`.
3. **SSH keys only** (as root, in the hPanel browser terminal or `ssh root@<ip>`). The `00-`
   file wins over Ubuntu's `50-cloud-init.conf`, which enables passwords:

   ```bash
   cat > /etc/ssh/sshd_config.d/00-hardening.conf <<'EOF'
   PasswordAuthentication no
   KbdInteractiveAuthentication no
   PermitRootLogin prohibit-password
   EOF
   systemctl restart ssh
   sshd -T | grep -Ei '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '
   ```

   The last command must print `passwordauthentication no`, `kbdinteractiveauthentication no` and
   `permitrootlogin without-password`. From your computer, a password login must now be refused
   with `Permission denied (publickey)`:
   `ssh -o PubkeyAuthentication=no -o PreferredAuthentications=password root@<ip>`.
4. **Updates, firewall and fail2ban** (as root):

   ```bash
   apt update && apt upgrade -y
   apt install -y fail2ban unattended-upgrades
   systemctl enable --now fail2ban
   ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
   ```

   PostgreSQL and the API are published on the server loopback only, so they stay unreachable
   from the internet even though Docker bypasses `ufw` for published ports.
5. **Docker** (skip the install with the Hostinger "Ubuntu with Docker" template), then the
   `deploy` user, without a password and with your key:

   ```bash
   curl -fsSL https://get.docker.com | sh
   adduser --disabled-password --gecos "" deploy && usermod -aG docker deploy
   install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   install -m 600 -o deploy -g deploy /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
   # keep only your own key there (drop the "#hostinger-managed-key" line):
   nano /home/deploy/.ssh/authorized_keys
   ```

   Check from your computer: `ssh deploy@<ip>` works and `docker ps` runs there.
6. **Read-only deploy key** for cloning (as `deploy`):

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "vps-ranking"
   cat ~/.ssh/github_deploy.pub   # GitHub → repository → Settings → Deploy keys, without write access
   GIT_SSH_COMMAND="ssh -i ~/.ssh/github_deploy" git clone git@github.com:alexandreluchetti/ranking-beach-tennis.git
   cd ranking-beach-tennis && git config core.sshCommand "ssh -i ~/.ssh/github_deploy"
   ```

## First deployment

```bash
# on the server, as deploy (repository cloned as above)
cd ~/ranking-beach-tennis
cp .env.example .env && chmod 600 .env   # then edit: POSTGRES_PASSWORD, DOMAIN, ADMIN_AUTH_SUBJECT
docker compose --profile prod up -d --build
docker compose --profile prod ps          # backend and postgres must be "healthy"
curl https://$DOMAIN/actuator/health      # {"status":"UP",...}
```

The first HTTPS request may take a few seconds while Caddy obtains the certificate. If DNS
does not point to the server yet, certificate issuance fails; fix DNS and restart `caddy`.

All four services (`postgres`, `backend`, `caddy`, `db-backup`) must be running, with
`postgres` and `backend` healthy. They use `restart: unless-stopped` and come back after a
server reboot. The production database starts empty: the administrator signs in first and
creates the season; every other player is created on their first sign-in.

### Verification (from any computer)

```bash
D=https://api.ranking-beach-tennis.tech
curl $D/actuator/health                          # 200 {"status":"UP",...}
curl -I http://api.ranking-beach-tennis.tech/    # 308 to https
curl -i $D/api/seasons                           # 401 without a token
curl -i -X OPTIONS $D/api/seasons -H "Origin: https://ranking-beach-tennis.web.app" \
  -H "Access-Control-Request-Method: GET"        # 200, Access-Control-Allow-Origin echoed
curl -i -X OPTIONS $D/api/seasons -H "Origin: https://other.example" \
  -H "Access-Control-Request-Method: GET"        # 403
```

Swagger must not open (`/swagger-ui.html` answers 401 because docs are disabled), and ports
5432 and 8080 must be closed from outside. Certificates (Let's Encrypt) are renewed by Caddy.

## Updates

```bash
git pull
docker compose --profile prod up -d --build backend
```

Flyway applies new migrations on startup. Take a manual backup first when a release contains
migrations: `docker compose --profile prod exec db-backup pg_dump --format=custom --file=/backups/before-update.dump`.

## Backups and restore

Dumps are written to `./backups` on the server, the **same disk** as the database: they do not
survive losing the server (rebuild, cancelled plan, disk failure). Copy them off the server
regularly, from your computer:

```bash
scp deploy@179.236.77.80:ranking-beach-tennis/backups/*.dump C:/Users/luche/ranking-backups/
```

Keep the copies **outside the repository** (they hold personal data; `*.dump` is git-ignored
anyway).

Hostinger VPS snapshots (hPanel → VPS → Snapshots) are a second, whole-server copy; check whether
the plan charges for them.

Restore (replaces the current data):

```bash
docker compose --profile prod stop backend
docker compose --profile prod exec -T db-backup pg_restore --clean --if-exists --no-owner \
  --dbname=ranking /backups/<file>.dump
docker compose --profile prod start backend
```

## Restarting the server

Rebooting the VPS (or a power cycle) keeps every piece of data: the database lives in the
`postgres-data` Docker volume and the dumps in `./backups`, both on the server's disk.

- Docker starts on boot (enabled by the official install script) and every service has
  `restart: unless-stopped`, so PostgreSQL, the API, Caddy and the backup job come back by
  themselves. The API is unavailable for a few minutes meanwhile.
- After an abrupt power cycle PostgreSQL replays its write-ahead log on start: committed data is
  kept; only a write in flight at that instant is lost.

Before restarting, check that Docker starts on boot (`enabled`):

```bash
systemctl is-enabled docker
```

After restarting, check the services (`backend` and `postgres` healthy) and the API:

```bash
cd ~/ranking-beach-tennis && docker compose --profile prod ps
curl https://api.ranking-beach-tennis.tech/actuator/health
```

**What does erase the data:** reinstalling the OS or rebuilding / changing the template of the VPS
in hPanel (formats the disk, dumps included), cancelling the plan, and `docker compose down -v`
(`-v` deletes the database volume; `down` without it is safe). Copy the dumps off the server
before any of these.

## Rebuilding after a compromise

Never clean a compromised server: attackers leave back doors (cron jobs, SSH keys). Rebuild it.

1. **Save the data**: take a dump (`docker compose --profile prod exec db-backup pg_dump
   --format=custom --file=/backups/incident.dump`), copy every dump to your computer (see
   "Backups and restore"), then **stop the VPS** in hPanel.
2. **Check the data** on your computer: restore the last dump from before the intrusion and the
   newest one into a throwaway PostgreSQL container and compare every table. Use the newest one
   only if nothing changed unexpectedly.
3. **Rotate every secret** the server held, even if you are unsure it was there:
   - hPanel password, and turn on two-factor authentication;
   - a new `POSTGRES_PASSWORD` (write a new `.env`, never copy the old one);
   - Firebase service account keys: Google Cloud Console → IAM & Admin → Service accounts →
     `firebase-adminsdk-…` → Keys: delete every key; create a new one only if push is used;
   - GitHub → repository → Settings → Deploy keys: delete the server's key (a new one is created
     in step 6 of the server setup);
   - your SSH key, if its private part was ever on the server.
4. **Reinstall the OS** in hPanel (VPS → OS & Panel → Operating system, Ubuntu 24.04) with a new
   root password and your SSH key. This formats the disk.
5. Follow **Server setup** from step 3 and **First deployment** up to `cp .env.example .env`, then
   restore the dump into the new, empty database before the API starts:

   ```bash
   # on your computer
   scp C:/Users/luche/ranking-backups/incident.dump deploy@179.236.77.80:
   # on the server, as deploy
   cd ~/ranking-beach-tennis && mkdir -p backups && mv ~/incident.dump backups/
   docker compose --profile prod up -d postgres db-backup
   docker compose --profile prod exec -T db-backup pg_restore --no-owner --dbname=ranking /backups/incident.dump
   docker compose --profile prod up -d --build
   ```

   Flyway finds the restored history and applies only newer migrations. Then run the
   verification commands of "First deployment".
6. Read the auth log for a few days (`grep -E "Accepted|Failed" /var/log/auth.log | tail`) and
   `fail2ban-client status sshd`.

## Incident history

- **2026-11-24 — server taken over through SSH.** An attacker (`117.72.96.16`) logged in as
  `deploy` with a guessed password: password logins were still enabled and `deploy` had a weak
  password. As `deploy` (docker group, root-equivalent) it installed a cryptominer in
  `/var/tmp` restarted by `deploy`'s crontab; Hostinger stopped the VPS on 2026-11-25. The
  database was compared table by table with the dump of 2026-11-23 and was unchanged, but the
  data and every secret on the server must be considered read. Response: rebuild with the
  security rules above.

## Operations

- Logs: `docker compose --profile prod logs -f backend` (also `caddy`, `db-backup`).
- Response times: every API request is logged as `GET /api/leagues/{leagueId}/seasons/current/ranking 200 12 ms`
  (route template, no ids). Average time and count per route over the last day, slowest last:

  ```bash
  docker compose --profile prod logs --since 24h backend | awk '/ ms$/ {r=$(NF-4)" "$(NF-3); s[r]+=$(NF-1); n[r]++} END {for (r in s) printf "%6.0f ms %6d  %s\n", s[r]/n[r], n[r], r}' | sort -n
  ```

  Backend logs are rotated by Docker (3 files of 10 MB).
- Health: `https://$DOMAIN/actuator/health` (status only, no details).
- Version: `https://$DOMAIN/actuator/info` (public, `{"build":{"version":"X.Y.Z","time":"..."}}` only).
- Database shell: `docker compose exec postgres psql -U ranking -d ranking`.
