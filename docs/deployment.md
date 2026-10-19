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
| Repository on the server | `/root/ranking-beach-tennis` (commands run as `root`) |

## Server setup (Hostinger)

1. **VPS** (hPanel → VPS): KVM plan (KVM 1 is enough), Ubuntu 24.04 LTS, a strong root password
   and your SSH public key (`ssh-keygen -t ed25519` on your computer). Shared hosting cannot run
   Docker.
2. **Domain and DNS** (hPanel → Domains → the domain → DNS / Nameservers): the domain must be
   registered and active (finish the contact data and the ICANN e-mail verification). Add an
   `A` record `api` → VPS IP. Check before starting Caddy:
   `nslookup api.ranking-beach-tennis.tech 8.8.8.8` must return the VPS IP. Starting Caddy
   before DNS resolves makes certificate issuance fail, and Let's Encrypt rate-limits retries.
   CAA records, if any, must allow `letsencrypt.org`.
3. **Base hardening** (as root, once):

   ```bash
   apt update && apt upgrade -y
   adduser deploy && usermod -aG sudo deploy
   mkdir -p /home/deploy/.ssh && cp ~/.ssh/authorized_keys /home/deploy/.ssh/ && chown -R deploy:deploy /home/deploy/.ssh
   ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
   ```

   After confirming `ssh deploy@<ip>` works in a second session, set `PasswordAuthentication no`
   and `PermitRootLogin no` in `/etc/ssh/sshd_config` and run `systemctl restart ssh`.
   PostgreSQL is published on the server loopback only, so it stays unreachable from the
   internet even though Docker bypasses `ufw` for published ports.
4. **Docker** (skip with the Hostinger "Ubuntu with Docker" template):
   `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker deploy`, then log in
   again.
5. **Read-only deploy key** for cloning:

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "vps-ranking"
   cat ~/.ssh/github_deploy.pub   # GitHub → repository → Settings → Deploy keys, without write access
   GIT_SSH_COMMAND="ssh -i ~/.ssh/github_deploy" git clone git@github.com:alexandreluchetti/ranking-beach-tennis.git
   cd ranking-beach-tennis && git config core.sshCommand "ssh -i ~/.ssh/github_deploy"
   ```

## First deployment

```bash
# on the server, as a non-root user in the docker group (repository cloned as above)
cd ranking-beach-tennis
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
scp root@179.236.77.80:/root/ranking-beach-tennis/backups/*.dump .
```

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
cd /root/ranking-beach-tennis && docker compose --profile prod ps
curl https://api.ranking-beach-tennis.tech/actuator/health
```

**What does erase the data:** reinstalling the OS or rebuilding / changing the template of the VPS
in hPanel (formats the disk, dumps included), cancelling the plan, and `docker compose down -v`
(`-v` deletes the database volume; `down` without it is safe). Copy the dumps off the server
before any of these.

## Operations

- Logs: `docker compose --profile prod logs -f backend` (also `caddy`, `db-backup`).
- Health: `https://$DOMAIN/actuator/health` (status only, no details).
- Version: `https://$DOMAIN/actuator/info` (public, `{"build":{"version":"X.Y.Z","time":"..."}}` only).
- Database shell: `docker compose exec postgres psql -U ranking -d ranking`.
