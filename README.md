# ranking-beach-tennis

Backend of the individual ranking app for a group of friends who play beach tennis: Spring Boot
API (Java 25, PostgreSQL, Flyway). The Flutter app lives in its own, independent repository.

## Structure

Single Maven project, Clean Architecture by package under `com.lucchetta`:

| Package | Responsibility |
|---|---|
| `core` | Business rules, domain, use cases and ports. No Spring, database or infrastructure. |
| `dataprovider` | Port implementations: PostgreSQL persistence, push (FCM). |
| `entrypoint` | REST controllers, DTOs, input validation, error handling. |
| `configuration` | Wiring of use cases, security, OpenAPI, push. |

Business decisions are recorded in the [decision log](docs/decisions.md). Branches, pull
requests and releases follow [CONTRIBUTING.md](CONTRIBUTING.md).

## Local development

Requirements: JDK 25, Maven 3.9+ (or the included wrapper), Docker.

Create your local `.env` once (development values) and start the local database:

```bash
cp .env.example .env
docker compose up -d
```

PostgreSQL listens on `127.0.0.1:5432`. Server deployment (API, HTTPS proxy, backups) uses the
same compose file with the `prod` profile: see [docs/deployment.md](docs/deployment.md).

Build and run all tests — unit tests and integration tests (`*IT`, real PostgreSQL via
Testcontainers; Docker must be running):

```bash
./mvnw verify
```

Run the API in a container together with the database (rebuilds the image from the current
code; `ADMIN_AUTH_SUBJECT`, `CORS_ALLOWED_ORIGINS` and the other settings come from `.env`):

```bash
docker compose --profile local up -d --build
```

Or run it from the IDE / Maven against the local database (`docker compose up -d`):

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` (loopback only) either way.

Swagger UI: http://localhost:8080/swagger-ui.html

| Variable | Description | Default |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Firebase **project ID** (text, not the project number) | `ranking-beach-tennis` |
| `ADMIN_AUTH_SUBJECT` | Firebase UID of the single administrator | empty (no admin) |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL connection | local docker-compose |
| `API_DOCS_ENABLED` | Exposes Swagger / OpenAPI (disable in production) | `true` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact origins of the web app allowed to call `/api/**` (no credentials, Bearer token only). Locally, run the web app on a fixed port (`flutter run -d chrome --web-port 5000`) and set `http://localhost:5000` | empty (no browser access) |
| `PUSH_FCM_ENABLED` | Sends push notifications through Firebase Cloud Messaging | `false` |
| `PUSH_FCM_CREDENTIALS_FILE` | Path to the Firebase service account key (JSON). Keep it outside the repository. | — |

In-app notifications always work; push is an optional best-effort copy.

### Administrator recovery

The administrator is whoever's Firebase UID is in `ADMIN_AUTH_SUBJECT`. To recover or transfer
admin access, set the new UID (Firebase console → Authentication → Users) and restart the
backend. The app has no feature to promote users.
