# Sentinel

Self-hosted API monitoring and incident detection, built as a Java/React modular monolith. Register GET endpoints, collect real availability and elapsed-time observations, and track outages from first failure through recovery.

**v0.1 is for trusted local use. There is no authentication. Do not expose the API or dashboard publicly. Run one backend instance only.**

## Features

- Monitor CRUD, configuration editing, pause/resume, and deletion with confirmation.
- Concurrent checks with bounded workers/queue, per-monitor overlap protection, stale-task expiry, and graceful shutdown.
- UP / DEGRADED / DOWN classification with distinct HTTP, timeout, DNS, TLS, connection, and blocked-destination failures.
- Incidents after three consecutive DOWN checks; UP or DEGRADED resets the sequence and resolves an active incident.
- Transactional check/incident persistence, PostgreSQL locking and uniqueness constraints, Flyway migrations, and paginated history.
- Responsive dark dashboard, real API data, response-time charts, status/activity summaries, and incident history.
- SSRF filtering at admission and connection-time DNS resolution, redirects disabled, and explicit local development exceptions.
- Backend unit and PostgreSQL integration tests; frontend tests at the HTTP boundary; build/test and Compose CI jobs.

## Stack and structure

Java 21 · Spring Boot 3.5.16 · Maven · PostgreSQL 17 · Flyway · Apache HttpClient 5 · React 19 · TypeScript · Vite · Recharts · Docker Compose

```text
backend/       Spring REST API, checker, scheduler, persistence, tests
frontend/      React application, typed API client, focused UI tests
docs/          Architecture, requirements, and verification report
.github/       Java, frontend, and Compose CI
compose.yaml   PostgreSQL + backend + nginx-served frontend
```

Read [architecture.md](docs/architecture.md) for component responsibilities, invariants, transaction boundaries, and trade-offs. [requirements.md](docs/requirements.md) maps requirements to acceptance criteria. [engineering-report.md](docs/engineering-report.md) records actual verification and remaining limits.

## Quick start with Docker

Requires Docker Engine/Desktop with Compose v2 and internet access to download images/dependencies. Java and Node are not needed on the host for this path.

```sh
cp .env.example .env
# Edit .env and replace DB_PASSWORD with your own local password.
docker compose up --build --wait
```

Open **http://localhost:3000**. The API is at **http://localhost:8080/api**. All published ports bind to `127.0.0.1`. Flyway applies migrations at backend startup, then Hibernate validates the schema. Compose waits for database/backend health before bringing up dependents.

```sh
docker compose logs -f backend
docker compose down
# Destructive: only when intentionally deleting all monitoring data:
# docker compose down --volumes
```

PostgreSQL data lives in the `postgres_data` named volume. Changing `.env` credentials does not change an existing database user's password; update that password deliberately or recreate a disposable database. Do not commit `.env`.

The Docker configuration is provided with a CI smoke test. Consult the verification report for whether it was actually executed in the implementation environment.

## Local development

Requires Java **21**, Maven **3.9+**, Node **22.12+** (Node 22 LTS recommended), npm, and PostgreSQL 17 or Docker for the database. Confirm `java -version` and `mvn -version` point to Java 21.

Start PostgreSQL and the API:

```sh
cp .env.example .env
# Set DB_PASSWORD first. Use shell-compatible values if sourcing the file.
docker compose up -d postgres
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_PORT:-5432}/${DB_NAME:-sentinel}"
cd backend
mvn spring-boot:run
```

In another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open the Vite URL (normally **http://127.0.0.1:5173**). `/api` is proxied to `127.0.0.1:8080`; no permissive CORS configuration is used. If the backend port changes, update `frontend/vite.config.ts`'s proxy. `VITE_API_BASE_URL` defaults to `/api`; absolute cross-origin URLs require an intentionally configured reverse proxy or narrow backend CORS policy. Browser environment variables are public and must never hold secrets.

### Checking an approved local endpoint

Production defaults reject local/private destinations. For deliberate local development, start the backend with the `dev` profile and an exact hostname allowlist:

```sh
SPRING_PROFILES_ACTIVE=dev APPROVED_LOCAL_HOSTS=127.0.0.1,localhost mvn spring-boot:run
```

Start a simple endpoint in another terminal, then register `http://127.0.0.1:9000/` in the UI:

```sh
python3 -m http.server 9000 --bind 127.0.0.1
```

The `dev` profile defaults to `localhost`, `127.0.0.1`, and `::1`. `APPROVED_LOCAL_HOSTS` replaces this list. These exceptions are opt-in and unavailable without the `dev`/`test` profile. Container `localhost` refers to the container itself, not the host machine. Do not enable development exceptions on a publicly reachable service.

## Configuration

| Variable/property | Default | Purpose |
| --- | --- | --- |
| `SERVER_ADDRESS` | `127.0.0.1` (Compose: `0.0.0.0` inside container) | Backend bind address |
| `DB_URL` | `jdbc:postgresql://localhost:5432/sentinel` | JDBC database URL |
| `DB_USER` | `sentinel` | Database username |
| `DB_PASSWORD` | Required | Database password |
| `CHECK_WORKERS` | 4 | Simultaneous checks; 1–64 |
| `CHECK_QUEUE_CAPACITY` | 16 | Pending checks; 1–1000 |
| `DB_POOL_SIZE` | 12 | JDBC connections |
| `SCHEDULER_ENABLED` | true | Enable background admission |
| `sentinel.scheduler.tick-ms` | 1000 | Due-monitor polling period |
| `sentinel.scheduler.max-queue-age` | 10s | Discard older queued work |
| `APPROVED_LOCAL_HOSTS` | Only in `dev` | Exact trusted development hosts |
| `VITE_API_BASE_URL` | `/api` | Frontend API base at build/dev time |

Monitor intervals are 5–86400 seconds, timeouts 100–30000 ms, and degradation thresholds 1–30000 ms and no greater than timeout. Names are 1–120 characters; URLs at most 2048 characters. A monitor's interval begins after the previous check is persisted. There is no exact fixed-rate scheduling guarantee.

## Tests and builds

```sh
cd backend
mvn test                 # Unit, real loopback HTTP, and MVC tests; no PostgreSQL required
mvn verify               # Above + package + PostgreSQL Testcontainers integration tests

cd ../frontend
npm ci
npm run typecheck
npm test
npm run build
```

Backend tests never require public endpoint access. Testcontainers needs a running Docker-compatible daemon and access to pull `postgres:17-alpine` (and its infrastructure images on first use). `mvn verify` deliberately fails if Docker is unavailable; it does not silently skip persistence tests. GitHub-hosted Ubuntu runners provide Docker.

Alternatively, run the same integration suite against an **expendable local PostgreSQL database**:

```sh
mvn verify \
  -Dsentinel.test.db-url=jdbc:postgresql://127.0.0.1:5432/sentinel_test \
  -Dsentinel.test.db-user=sentinel \
  -Dsentinel.test.db-password=your-test-password
```

**The integration suite truncates `monitors` and cascades to history before each test. Never point it at a database containing data you need.** The default path uses an isolated Testcontainer instead. Frontend tests mock HTTP responses with MSW; the shipped application always calls the real backend.

### Live scheduler smoke test

With the backend running under the `dev` profile (allowing `127.0.0.1`) and Vite running:

```sh
python3 scripts/live-smoke.py --frontend http://127.0.0.1:5173
```

This owns a temporary loopback HTTP server, creates one test monitor, waits for real scheduled failures/recovery, checks edit/pause/resume and the frontend proxy, and deletes its test monitor. It normally takes about 20 seconds. Omit `--frontend` for API-only verification. It requires a backend running on the same host as the Python fixture.

## API

All timestamps are ISO-8601 UTC. Lists return `{ content, page, size, totalElements, totalPages }`; pages are zero-based and sizes capped at 100. Check and incident histories are newest first. A newly created monitor has null current status until its first result.

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/monitors` | Create, return 201 + Location |
| GET | `/api/monitors?page=0&size=20` | Paginated monitors |
| GET | `/api/monitors/{id}` | Details and latest observation |
| PUT | `/api/monitors/{id}` | Replace configuration |
| PATCH | `/api/monitors/{id}/enabled` | Pause/resume |
| DELETE | `/api/monitors/{id}` | Delete monitor and history, return 204 |
| GET | `/api/monitors/{id}/checks?page=0&size=50` | Check history |
| GET | `/api/monitors/{id}/incidents?page=0&size=20` | Monitor incidents |
| GET | `/api/incidents?page=0&size=20` | Global incident history |
| GET | `/api/dashboard` | Counts and latest 12 observations |
| GET | `/actuator/health` | Application/database health |

Example requests (the first URL must resolve publicly, unless using explicit development exceptions):

```sh
curl -i http://localhost:8080/api/monitors \
  -H 'Content-Type: application/json' \
  -d '{"name":"Example API","url":"https://example.com/","intervalSeconds":60,"timeoutMs":5000,"degradationThresholdMs":1000,"enabled":true}'

curl 'http://localhost:8080/api/monitors?page=0&size=20'
# Replace <id> with the returned UUID:
curl 'http://localhost:8080/api/monitors/<id>/checks?page=0&size=50'
curl -X PATCH http://localhost:8080/api/monitors/<id>/enabled \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
curl -X DELETE http://localhost:8080/api/monitors/<id>
```

Illustrative creation response (IDs/times vary):

```json
{
  "id": "47ddc776-b03b-4b69-8bbf-ec2748fc7dde",
  "name": "Example API",
  "url": "https://example.com/",
  "intervalSeconds": 60,
  "timeoutMs": 5000,
  "degradationThresholdMs": 1000,
  "enabled": true,
  "createdAt": "2026-11-06T21:00:00Z",
  "updatedAt": "2026-11-06T21:00:00Z",
  "currentStatus": null,
  "lastCheckedAt": null,
  "latestDurationMs": null,
  "latestHttpStatus": null
}
```

A timeout observation has `"httpStatus": null`, `"status": "DOWN"`, and `"failureReason": "TIMEOUT"`; it is never fabricated as HTTP 500. `durationMs` is elapsed time to failure. A real HTTP 503 has `httpStatus: 503` and `failureReason: "HTTP_ERROR"`.

Structured error example (400):

```json
{
  "timestamp": "2026-11-06T21:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Check the submitted fields",
  "errors": { "intervalSeconds": "must be greater than or equal to 5" }
}
```

Malformed requests/invalid pagination return 400, missing resources 404, and unexpected failures a sanitized 500. URL validation failures return `INVALID_DESTINATION` with a `url` field error. No full URL or raw network exception is intentionally logged.

## Semantics and limitations

- DOWN is an observation; an incident opens only after three consecutive DOWN observations. DEGRADED breaks that sequence and also resolves an incident.
- Incident start is the first failed check's start time, not the third failure's time. Active duration is calculated at read time.
- Pausing does not resolve an incident or reset its failure sequence. Editing preserves the logical monitor's history. Create a new monitor for a distinct endpoint identity.
- Dashboard counts use last observed status, including paused monitors; pending monitors are reported separately. Dates render in the browser's local timezone.
- Checks measure GET response-header latency, not full response-body transfer or business correctness. Redirects are DOWN. No custom request headers, credentials, request bodies, or assertions are supported.
- Queue saturation and stale queued work produce skipped checks, not artificial failures. Sampling under load is approximate.
- One backend only. Multi-instance scheduling needs shared claims/leases and fencing; database incident locks alone are insufficient.
- SSRF policy validates actual resolved destinations, but does not replace network isolation. System DNS can outlive the request deadline. Network routing/NAT can undermine assumptions about address reachability. This is not safe for unrestricted public exposure.
- History has no retention policy yet; disk usage grows. Offset pages can shift as new results arrive.
- Authentication, authorization, alert delivery, uptime/SLO reporting, and deployment infrastructure are intentionally outside v0.1.

## Next improvements

Add access controls before remote use; introduce retention and scheduler saturation metrics; harden DNS deadlines and egress isolation; add configurable alert delivery and uptime/SLO definitions; move large histories to cursor pagination. Discuss lease/fencing semantics before any multi-instance support.
