# WikiLive.Api

Kotlin/Ktor backend for WikiLive. Replaces the legacy C# ASP.NET Core API.

## Stack

- **Ktor 3.0.3** — HTTP server & client
- **Exposed 0.57.0 (DSL)** — SQL framework
- **PostgreSQL** — database with `jsonb` columns
- **HikariCP** — connection pool
- **kotlinx.serialization** — JSON handling
- **Testcontainers** — integration tests
- **Gradle (Kotlin DSL)** — build tool

## Requirements

- JDK 21+ (tested with Eclipse Temurin 21)
- PostgreSQL 16+ (or use Docker Compose)

## Quick Start

### 1. Dev mode (port 5132)

```bash
./gradlew run
```

The API will be available at `http://localhost:5132`.

### 2. Docker Compose

```bash
docker compose up --build -d
```

Exposes API on `http://localhost:8080` and PostgreSQL on `localhost:5432`.

## Configuration

Edit `src/main/resources/application.conf` or override via environment variables:

| Variable   | Default                              | Description          |
|------------|--------------------------------------|----------------------|
| `PORT`     | `5132`                               | HTTP server port     |
| `DB_URL`   | `jdbc:postgresql://localhost:5432/wiki` | JDBC connection URL  |
| `DB_USER`  | `postgres`                           | Database user        |
| `DB_PASSWORD` | `06`                              | Database password    |

## API Overview

All routes are prefixed with `/api` and use **PascalCase** for compatibility with the existing frontend.

### Pages

| Method | Endpoint                                | Description                         |
|--------|-----------------------------------------|-------------------------------------|
| GET    | `/api/Pages`                            | List all pages                      |
| GET    | `/api/Pages/{id}`                       | Get page with comments & versions   |
| POST   | `/api/Pages`                            | Create a new page                   |
| PUT    | `/api/Pages/{id}`                       | Update page (creates a version)     |
| DELETE | `/api/Pages/{id}`                       | Delete page + cascade               |
| POST   | `/api/Pages/{id}/restore/{versionId}`   | Restore page to a previous version  |

### Comments

| Method | Endpoint                     | Description            |
|--------|------------------------------|------------------------|
| POST   | `/api/Comments`              | Create a comment       |
| POST   | `/api/Comments/{id}/like`    | Increment likes        |
| PUT    | `/api/Comments/{id}/resolve` | Mark as resolved       |
| DELETE | `/api/Comments/{id}`         | Delete a comment       |

### MwsProxy

Proxies requests to `https://fusion.mws.ru`. Falls back to `Authorization: Bearer usk-test-token` if no auth header is provided.

| Method | Endpoint                                          | Proxy target                                          |
|--------|---------------------------------------------------|-------------------------------------------------------|
| GET    | `/api/MwsProxy/spaces`                            | `/fusion/v1/spaces`                                   |
| GET    | `/api/MwsProxy/spaces/{spaceId}/nodes`            | `/fusion/v1/spaces/{spaceId}/nodes`                   |
| GET    | `/api/MwsProxy/datasheets/{dstId}/fields`         | `/fusion/v1/datasheets/{dstId}/fields`                |
| GET    | `/api/MwsProxy/datasheets/{dstId}/records`        | `/fusion/v1/datasheets/{dstId}/records`               |
| PATCH  | `/api/MwsProxy/datasheets/{dstId}/records`        | `/fusion/v1/datasheets/{dstId}/records`               |

## Swagger UI

Open `http://localhost:5132/` (or `:8080` in Docker) to see Swagger UI.

## Testing

```bash
./gradlew test
```

Integration tests use **Testcontainers** with PostgreSQL 16. A running Docker engine is required for the test suite to execute.

## Build

```bash
./gradlew build
```

Fat JAR is produced at `build/libs/WikiLive.Api-all.jar`.

## Notes

- **CORS** is configured wide open (`anyHost()`) for local frontend development.
- **Port 5132** is used in dev to match the Vite proxy config in `WikiLive.Client`.
- **JSON responses** skip `null` fields (`explicitNulls = false`) to match the legacy C# contract.
- **Cascade deletes** are handled at the PostgreSQL level (`ON DELETE CASCADE`).

## Legacy

The original C# ASP.NET Core API was moved to [`WikiLive.Api.Legacy`](../WikiLive.Api.Legacy).
