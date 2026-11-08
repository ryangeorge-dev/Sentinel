# MVP requirements and acceptance criteria

Version: 0.1. Scope: trusted, self-hosted, single-backend-instance monitoring of HTTP GET endpoints.

| Area | Acceptance criteria | Verification |
| --- | --- | --- |
| Monitor management | UUID, name, URL, bounded interval/timeout/threshold, enabled flag, creation/modification timestamps; CRUD and pause/resume; DTOs only | MVC validation and PostgreSQL CRUD integration tests; UI interaction tests |
| HTTP checking | 2xx within threshold UP, above threshold DEGRADED; non-2xx and transport failures DOWN; elapsed time uses monotonic clock; no fabricated HTTP status | Loopback HTTP tests for 204, 503, redirect, timeout, connection refusal; injected DNS failure and monotonic ticker |
| Scheduling | Different monitors execute concurrently; bounded workers and queue; one pending/active task per monitor; rejection/exception/stale-task cleanup; shutdown rejects new work | Latch-driven scheduler tests, stale queue clock, saturation and retry assertions |
| Incidents | Three consecutive DOWN results open an incident at first failure; UP/DEGRADED reset and recover; no duplicate active incidents | Unit transition tests, concurrent PostgreSQL writes, partial unique-index integration test |
| Atomicity | Check persistence, failure counter, latest status, due time and incident transition commit or roll back together | Injected incident-write failure verifies check and counter rollback |
| Mutation races | Disabled, deleted or reconfigured monitor discards outdated in-flight observations | Revision checks and unit/integration tests |
| PostgreSQL | Flyway migrations, UTC timestamptz, UUID keys, foreign keys/cascade, indexes and constraints; Hibernate validate only | Real PostgreSQL integration suite and startup schema validation |
| History | Paginated monitor/check/incident endpoints; size capped at 100; deterministic timestamp/UUID ordering | MVC bounds and integration paging assertions |
| REST | Correct creation/deletion statuses; structured field errors; sanitized unexpected errors; invalid IDs/paging rejected | MockMvc and PostgreSQL HTTP integration tests |
| SSRF | Only HTTP/HTTPS, no embedded credentials/fragments; public addresses only, all DNS answers validated; literal addresses checked; redirects disabled; connection-time DNS filtering | Public/non-public address table tests, mixed DNS, rebinding and loopback blocking tests |
| Development exceptions | Exact allowlisted local hosts require dev/test profile; production defaults unchanged | Policy profile tests; integration test profile |
| Dashboard | Real API totals, UP/DEGRADED/DOWN, pending count, active incidents, recent activity; empty/loading/error states | MSW network-boundary React tests and manual live integration |
| Monitor UI | Add/edit/view/pause/resume/delete; validated inputs; delete confirmation; rejected inputs preserved | React interaction tests and live integration |
| Latency/history UI | Chronological real data, ms axis, red failed points, gaps for missing durations, insufficient-data state, exact-value table, pagination | Frontend component tests and browser review |
| Incident UI | Monitor name, start/recovery, duration, explicit active/resolved labels | React incident table test and backend integration |
| Local development | Environment examples, database/backend/frontend containers, health checks, loopback bindings, documented local commands | Compose CI smoke job; local execution status recorded separately |
| CI | Java 21 build/unit/PostgreSQL integration tests, Node frontend tests/typecheck/build, Compose smoke test | GitHub Actions workflow; execution only after pushed by repository owner |
| Documentation | Setup, APIs, architecture/invariants/trade-offs, requirements, test results and known limits | README, architecture and engineering report |

## Important interpretation choices

- Check interval is a delay after persisted completion, not a promise of exact wall-clock sampling.
- A skipped task is not a health observation. It does not increment/reset incident counters.
- Failed checks still record elapsed time; charts label that as time to failure, not successful server response time.
- Pausing/editing preserves the logical monitor's outage state. The next UP/DEGRADED observation is the recovery evidence.
- Deleting a monitor intentionally deletes all its history; the UI requires confirmation.
- Unchecked monitors are PENDING in the UI and null status in the API, not UP.
- The latency threshold may equal the timeout; values above timeout are rejected.
- Default Testcontainers integration tests must fail, not silently skip, when Docker is unavailable. An explicit disposable external database override supports local verification.

## Explicit exclusions

Authentication, authorization, public exposure, notifications, uptime percentages/SLOs, retention automation, distributed scheduling, custom HTTP methods/headers/bodies, redirect following, business-response assertions, cloud deployment, Redis, Kafka, Kubernetes, and microservices.

## Completion rule

Implemented code is not itself proof of acceptance. See `engineering-report.md` for the exact builds/tests executed and any unmet verification requirements. Docker/Testcontainers execution and CI must not be reported as successful unless run successfully in their respective environments.
