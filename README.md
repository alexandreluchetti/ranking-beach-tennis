# Ranking Beach Tennis

Individual ranking app for a group of friends who play beach tennis.

## Repository layout

| Path | Content |
|---|---|
| `backend/` | Spring Boot API (Java 25, PostgreSQL, Flyway) — created in stage 1 |
| `mobile/` | Flutter app for Android and iOS — created in stage 7 |
| `docs/` | Technical documentation and the [decision log](docs/decisions.md) |

## Local development

Requirements: JDK 25, Maven 3.9+, Docker, Flutter SDK.

Start the local database:

```bash
docker compose up -d
```

PostgreSQL listens on `127.0.0.1:5432` (credentials in `.env.example`, development only).

### Backend

The backend requires JDK 25. Point `JAVA_HOME` to it, then from `backend/`:

```bash
./mvnw verify
```

Runs unit tests and integration tests (`*IT`, real PostgreSQL via Testcontainers — Docker must
be running).

```bash
FIREBASE_PROJECT_ID=<project-id> ./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

| Variable | Description | Default |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Firebase **project ID** (text, not the project number). Required. | — |
| `ADMIN_AUTH_SUBJECT` | Firebase UID of the single administrator | empty (no admin) |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL connection | local docker-compose |
| `API_DOCS_ENABLED` | Exposes Swagger / OpenAPI (disable in production) | `true` |

#### Administrator recovery

The administrator is whoever's Firebase UID is in `ADMIN_AUTH_SUBJECT`. To recover or transfer
admin access, set the new UID (Firebase console → Authentication → Users) and restart the
backend. The app has no feature to promote users.
