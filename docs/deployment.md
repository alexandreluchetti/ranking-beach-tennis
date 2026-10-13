# Deployment (single VPS with Docker Compose)

Everything runs on one Linux server from `docker-compose.yml` with the `prod` profile:

| Service | Role | Exposed |
|---|---|---|
| `caddy` | HTTPS reverse proxy, automatic Let's Encrypt certificates | 80, 443 |
| `backend` | Spring Boot API (built from `Dockerfile`) | only to `caddy` |
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

## First deployment

```bash
# on the server, as a non-root user in the docker group
git clone <repository-url> ranking-beach-tennis && cd ranking-beach-tennis
cp .env.example .env && chmod 600 .env   # then edit: POSTGRES_PASSWORD, DOMAIN, ADMIN_AUTH_SUBJECT
docker compose --profile prod up -d --build
docker compose --profile prod ps          # api must be "healthy"
curl https://$DOMAIN/actuator/health      # {"status":"UP",...}
```

The first HTTPS request may take a few seconds while Caddy obtains the certificate. If DNS
does not point to the server yet, certificate issuance fails; fix DNS and restart `caddy`.

## Updates

```bash
git pull
docker compose --profile prod up -d --build backend
```

Flyway applies new migrations on startup. Take a manual backup first when a release contains
migrations: `docker compose --profile prod exec db-backup pg_dump --format=custom --file=/backups/before-update.dump`.

## Backups and restore

Dumps are written to `./backups` on the server. Copy them elsewhere regularly
(`scp user@server:ranking-beach-tennis/backups/*.dump .`).

Restore (replaces the current data):

```bash
docker compose --profile prod stop backend
docker compose --profile prod exec -T db-backup pg_restore --clean --if-exists --no-owner \
  --dbname=ranking /backups/<file>.dump
docker compose --profile prod start backend
```

## Operations

- Logs: `docker compose --profile prod logs -f backend` (also `caddy`, `db-backup`).
- Health: `https://$DOMAIN/actuator/health` (status only, no details).
- Database shell: `docker compose exec postgres psql -U ranking -d ranking`.
