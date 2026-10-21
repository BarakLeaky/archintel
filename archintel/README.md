# ArchIntel
**Research. Discover. Analyze. Architect. Build.**

Evidence-backed research and intelligence platform (Spring Boot + Angular + PostgreSQL).
Principle: *research first, evidence second, intelligence third, generation last.*

## Status: Phase 1.5 (backend foundation)

| Area | State |
|---|---|
| Repo layout, Docker Compose, `.env.example` | done |
| Flyway schema V1 (core) + V2 (evidence, results, notifications, delivery log, GitHub repo analysis, duplicate provenance) | written, **not yet applied to a real PostgreSQL** |
| Date-aware newness classifier | done, tests pass |
| Registration / login, BCrypt, stateless JWT, ownership-checked projects API | written, **Spring tests not yet run** |
| `ResearchSourceConnector` interface + GitHub connector (search, pagination, dedup, README/manifest/language inspection) | done, tests pass against a fake GitHub |
| Resilient HTTP client (timeouts, retry/backoff, rate-limit waiting, ETag caching, same-origin redirects) | done, tests pass |
| URL canonicalisation + content hashing for dedup | done, tests pass |
| Research pipeline, evidence extraction, scoring, AI synthesis | **not started** (Phase 2) |
| Subscriptions, scheduler, email / WhatsApp / in-app delivery | **not started** (Phase 3) |
| Angular frontend, frontend tests, CI for frontend | **not started** (Phase 4) |

### Verification status (be skeptical of anything not listed as run)
- **Run and passing (42 tests):** newness, URL canonicalisation, hashing, query validation, the resilient HTTP client against a local fake server, and the GitHub connector against a local fake GitHub. These were run with a stand-in for the JUnit/Jackson APIs because the authoring sandbox had no network or Maven.
- **Written but never executed:** `JwtServiceTest`, `AuthServiceTest`, `ProjectServiceTest`, `AuthControllerTest`, `ProjectControllerTest`, `SchemaMigrationTest`, the Maven build, the Dockerfile, `docker compose up`, and `ci.yml`. Expect to fix a few compile or wiring errors on first run.
- **Never called:** the real GitHub API. The connector follows the documented REST API but has only seen fixtures.

## First run on your machine
Requires JDK 21, Maven 3.9+, and Docker (for PostgreSQL and the schema test).

    cd backend
    mvn verify        # runs all tests; SchemaMigrationTest is skipped if Docker is unavailable

`SchemaMigrationTest` is the important one: it applies V1+V2 to a real PostgreSQL and makes Hibernate validate every entity against it. Paste any failures back and they get fixed.

To run the stack:

    cp .env.example .env     # then edit it
    docker compose up --build

The backend **refuses to start** unless `JWT_SECRET` is a random value of at least 32 bytes (the `.env.example` placeholder is rejected on purpose).

## Environment
REQUIRED: `POSTGRES_DB`, `POSTGRES_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGIN`, `APP_BASE_URL`.
OPTIONAL (feature disabled when unset): `GITHUB_TOKEN` (works without, at lower rate limits), `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `TAVILY_API_KEY`, `MAIL_*`, `WHATSAPP_*`.
Generate a secret with `openssl rand -base64 48`.

## API (current)
| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| GET | `/api/users/me` | Bearer JWT |
| POST/GET | `/api/projects` | Bearer JWT |
| GET/DELETE | `/api/projects/{id}` | Bearer JWT, owner only (others get 404) |
| GET | `/actuator/health` | public |

All errors share one JSON shape: `{timestamp, status, error, message, fieldErrors?}`.

## Docs
- [docs/security.md](docs/security.md)
- [docs/github-integration.md](docs/github-integration.md)

## Author
Leaky Barak — Nairobi, Kenya | Remote — https://github.com/BarakLeaky
