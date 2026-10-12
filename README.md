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
