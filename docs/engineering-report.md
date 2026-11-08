# Initial MVP engineering report

Date: November 6, 2026. This report distinguishes implemented functionality from executed verification. No commits or pushes were made.

## 1. Implemented features

Monitor creation/list/detail/edit/pause/resume/deletion; periodic HTTP GET checking; UP/DEGRADED/DOWN observations; transactional incident detection and recovery; paginated check and incident histories; real-data dashboard and latency charts; structured validation/errors; environment configuration; Dockerfiles/Compose; GitHub Actions; README and architecture/requirements documentation.

The repository was initially empty. A standalone Git repository was initialized to avoid inheriting an unrelated home-directory repository. All implementation remains uncommitted.

## 2. Backend architecture

Java 21 / Spring Boot 3.5.16 modular monolith. Feature/responsibility packages separate monitoring, checking, scheduling, incidents, and shared infrastructure. Controllers accept validated DTOs and return DTOs, never entities. Network I/O is outside transactions. A dedicated result recorder owns the check/incident transaction. A configurable UTC clock supports deterministic state-transition tests; monotonic time measures network elapsed duration.

## 3. Frontend architecture

React/TypeScript/Vite routes cover overview, monitor details, and incidents. A centralized typed API client handles JSON and error responses. `useResource` provides abortable fetching, loading/error states, and non-overlapping ten-second polling. Local component state handles forms and mutations. Reusable status, pagination, incident, form, and latency components avoid global state machinery. Recharts loads on demand. Vite and nginx proxy `/api`.

Frontend production output uses actual HTTP API requests. MSW fixtures exist only in tests. Failed elapsed times are marked red; null durations are not plotted as zero. Exact observations are available in a table.

## 4. Database schema

- `monitors`: UUID, configuration, timestamps, configuration revision, next due time, latest observation, consecutive failure count and first failure timestamp.
- `check_results`: UUID, monitor foreign key, UTC check time, optional HTTP status, elapsed ms, health and failure reason.
- `incidents`: UUID, monitor foreign key, UTC start and optional recovery. Duration is derived at query time.

Two Flyway migrations create the schema and enforce consistent health/status/failure combinations. Foreign keys cascade on monitor deletion. Indexed monitor/time history queries and global time indexes support pagination. A partial unique index permits exactly one unrecovered incident per monitor. Hibernate uses schema validation rather than creation.

## 5. Concurrency and scheduling

Bounded fixed worker pool and bounded queue; atomic per-monitor reservation before submission; duplicates skipped while queued or running. Rejection and task `finally` release reservations. Stale queued work expires, execution re-reads configuration, and stale revisions are discarded at persistence. Due time is completion plus interval, so no catch-up backlog accumulates. Shutdown drains queued tasks and waits for running work before interrupting stragglers.

One backend instance is supported. Multiple instances need shared execution claims/leases with fencing; the incident row lock does not prevent duplicate outbound checks across processes.

## 6. Incident behavior

Three consecutive DOWN observations open an incident at the first failure timestamp. UP and DEGRADED both reset the count and recover an active incident. DOWN counts saturate at three. Row locking serializes transitions; the partial index independently prevents duplicate open incidents. Check history, latest status, due time, counters, and incident changes commit together. Pausing/editing is not recovery evidence and retains existing outage state. Deleting intentionally removes history.

## 7. Security and operational limits

HTTP/HTTPS only; no embedded credentials or fragments. Admission and per-check literal-address validation reject non-public destinations. A custom Apache DNS resolver validates every returned address at connection time; mixed public/private DNS responses are rejected. Redirects, retries, cookies and connection reuse are disabled. The client does not opt into system proxies. TLS verification remains enabled. Request/connection deadlines are explicit, and response bodies are immediately closed rather than buffered.

Backend defaults to loopback. Compose publishes only loopback ports and binds the backend to all interfaces only within its container network. Exact local destination exceptions require an explicit dev/test profile. Logs intentionally omit URL contents and raw network messages.

No authentication or authorization exists. Do not expose this application publicly. Address checks are not a network isolation boundary: routing/NAT and public proxying services remain relevant. Java system DNS can exceed the request timeout and resist interruption, potentially tying up bounded workers. Stored URL query strings are visible to trusted API/UI users. There is no retention policy, so history grows until deleted.

## 8. Executed verification

Environment: macOS arm64; downloaded temporary Eclipse Temurin **21.0.13** for exact Java 21 verification; Maven 3.9.16; Node 26.13.0/npm 11.20.0; disposable PostgreSQL **17.5** on loopback port 55432. Node 22 is configured in CI and Docker, but was not the local test runtime.

| Check | Result |
| --- | --- |
| Backend compile, unit tests, packaging | PASS |
| Scheduler tests | 6 passed |
| HTTP checker tests | 10 passed |
| SSRF/destination policy cases | 42 passed |
| REST validation tests | 4 passed |
| Incident transition unit tests | 5 passed |
| PostgreSQL integration tests | 7 passed |
| Total backend tests | **74 passed, 0 failed, 0 skipped** |
| Frontend Vitest/RTL tests | **12 passed** across 2 files |
| TypeScript checking | PASS |
| Vite production build | PASS |
| Compose/CI/application YAML parsing | PASS (syntax only) |
| Real API/scheduler/Vite-proxy smoke test | PASS; see evidence below |
| Browser visual/responsive review | NOT RUN: computer-use tool reported no browser available |
| Docker image build / Compose runtime | NOT RUN: Docker executable/daemon unavailable |
| Testcontainers container provisioning path | NOT RUN: Docker unavailable; same integration tests used explicit disposable PostgreSQL override |
| GitHub Actions hosted execution | NOT RUN: workflow created locally, not pushed |

Commands executed successfully (temporary paths were environment-specific):

```sh
# backend/
JAVA_HOME=/private/tmp/sentinel-jdk21/Contents/Home \
  mvn -Dmaven.repo.local=/private/tmp/sentinel-m2 -B -ntp test

JAVA_HOME=/private/tmp/sentinel-jdk21/Contents/Home \
  mvn -Dmaven.repo.local=/private/tmp/sentinel-m2 -B -ntp \
  -Dsentinel.test.db-url=jdbc:postgresql://127.0.0.1:55432/postgres \
  -Ddebug=false verify

# frontend/
npm install --cache /private/tmp/sentinel-npm --fetch-retries=1 --fetch-timeout=30000
npm test
npm run typecheck
npm run build

# repository root, while the dev-profile backend and Vite are running:
python3 scripts/live-smoke.py --frontend http://127.0.0.1:5173
```

The live smoke test owns a loopback HTTP fixture and a temporary monitor. Three actual scheduled HTTP 503 responses persisted as DOWN/HTTP_ERROR, opened one incident at the first failure, and a later HTTP 200 resolved it. Editing, pause/resume, frontend HTML delivery, the Vite API proxy, and monitor/history deletion were verified. The smoke-test monitor and its history were removed, and temporary services were stopped after verification.

The seven PostgreSQL tests exercise both Flyway migrations and Hibernate validation, CRUD and history pagination, eight concurrent failure recordings producing one incident, recovery/reopening, direct database uniqueness enforcement, rollback when incident persistence fails, stale result rejection, private-address rejection, and contradictory check data rejection. They run against real PostgreSQL, not H2 or mocks of persistence.

A regression test found that a second response-close operation could overwrite a successful streaming response with a transport failure. The implementation now closes the response exactly once with immediate close. The final suite includes the passing unfinished-body regression test. API review also added fractional-integer rejection and correct unsupported-content-type errors.

Non-failing toolchain warnings remain: Mockito dynamically attaches its test agent under Java 21; HttpClient's per-request connection-timeout setter is deprecated but supported; the host's Node 26 emits an experimental localStorage warning in jsdom. There are no failing test assertions in the final run.

## 9. Incomplete verification and known limitations

No known failing functional requirement remains in the executed local API/backend/frontend tests. This does **not** certify Docker or browser behavior. The definition of done is not fully verified until the Compose smoke job and default Testcontainers path run on Docker, and the responsive UI receives browser review. The GitHub Actions workflow has backend, frontend, and Compose jobs ready for that verification.

Intentional v0.1 limits: no access controls, alerts, retention, uptime percentages/SLOs, distributed scheduling, request customization, or body assertions. Offset pagination can shift as observations arrive. Conservative address policy may reject unusual legitimate public destinations. System DNS cannot be given a strict interruption guarantee with this implementation. Paused status counts remain last observed status; paused incidents remain active until real recovery evidence.

## 10. Recommended next improvements

1. Run `docker compose up --build --wait` and default `mvn verify` on a Docker-enabled host; review desktop/mobile behavior in a real browser.
2. Add authentication/authorization and network egress restrictions before considering remote exposure.
3. Add retention configuration, scheduler saturation/skipped-work metrics, and explicit stale-status indications.
4. Replace system DNS with a deadline-capable resolver where strict shutdown guarantees are required.
5. Define notification delivery and uptime/SLO semantics before implementing them.
6. Add cursor pagination and configuration audit history as histories grow.
7. Discuss database lease expiry, fencing, and failure recovery before allowing multiple scheduler instances.

For an engineering walkthrough, start with `CheckScheduler.submit`, then trace `HttpChecker.check` into `ResultRecorder.record`. The key distinction is **execution ownership in memory** versus **state consistency in the database**: each solves a different concurrency problem.
