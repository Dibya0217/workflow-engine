# Verification record

Backend checks: 2026-11-02. Final frontend/browser checks: 2026-11-03. Machine: macOS Intel. Exact target: Java 21 (Temurin 21.0.13), Maven Wrapper 3.9.11. The repository initially had no application code beyond a hello-world class. The existing `.idea/workspace.xml` user modification was preserved.

## Executed successfully

- Java compilation and packaged Spring Boot executable JAR; final post-format package build passed on 2026-11-03.
- `./mvnw verify`: **11 unit tests + 23 integration tests**, zero failures, zero errors, zero skipped tests.
- Unit coverage: valid/invalid graphs, cycles/disconnected tasks/duplicate names/unknown types, terminal and retry transitions, capped exponential backoff, handler validation and duplicate handler registration.
- Engine integration coverage (12): real PostgreSQL/Flyway/JPA; nested configuration JSONB round trip; full sequence; reconstructed engine after lost provider result; completed task preserved; stable provider result reused; stale completion rejected; retry deadline persistence; permanent failure/manual retry; pause; cancel; duplicate completion; concurrent claim ownership; immutable versions; concurrent provider idempotency; exhausted leases. Several assertions share a test.
- Operations integration coverage (4): outbox failure/backoff and stale publisher fencing; expired relay lease; transactional outbox rollback; concurrent schedule ticks and coalesced missed intervals.
- Kafka integration coverage (2): actual embedded broker command/outbox/worker/result path and duplicate commands; poison command publication to DLT with original-topic header. No mocked broker.
- Security/API integration coverage (5): real JWT login/security filter chain, unauthenticated rejection, generic invalid credentials, USER versus ADMIN permissions, create/activate/start endpoints and invalid request/pagination handling.
- Frontend TypeScript production build and Vite bundling passed, including the final post-format build on 2026-11-03; React Flow designer loaded as a separate chunk.
- Frontend Vitest: **6 tests**, all passed (connected graph, cycle, disconnected graph, duplicate names, retry bounds, missing successor).
- Live HTTP checks: frontend 200; management readiness 200; Prometheus scrape 200.
- `git diff --check` passed; Java and frontend sources formatted with Google Java Format and Prettier.
- Compose, CI and observability YAML parsed successfully by Prettier; the secret initialization script passed `sh -n`.
- OpenAPI JSON returned 200 with 20 documented path patterns; proxied Swagger returned 200. Loopback-origin login returned 200 with the expected CORS header.

## Defect found and fixed during browser verification

The native demo uses the Kafka test dependency graph, including Scala Jackson modules. Hibernate's implicit JSON mapper auto-discovered those unrelated modules and deserialized nested untyped arrays into an unexpected collection representation. A required-fields setting stopped behaving as a Java list. The fix explicitly supplies Spring's ObjectMapper to Hibernate's JSON format mapper, and adds a real PostgreSQL regression that checks the nested list and missing-field failure. Validation now also fails safely when requiredFields is not an array.

## Browser verification

A live local demo uses real PostgreSQL, real handlers and actual API calls. Generated credentials live only in ignored `.cache/demo-login.json`. Dashboard renders real execution counts. Verified on 2026-11-03:
- Created `browser-verified-order` in React Flow with validation and payment tasks, JSON configuration and automatic sequential connection.
- Validated against the backend, saved version 1, activated it and started it through the run dialog.
- Observed COMPLETED, validation attempt_count=1, payment attempt_count=2, a persisted retry deadline, final payment output and all 8 audit events.
- Captured desktop dashboard/designer/execution screenshots and a mobile dashboard screenshot.
- Verified mobile navigation and a 390px viewport with document scroll width exactly 390px (no page overflow); restored viewport afterward.
- Backend restart demonstrated expiration of the previous in-memory JWT session and successful login with the newly generated local-demo account.

Screenshots: `docs/screenshots/dashboard.png`, `designer.png`, `execution.png`, `mobile.png`.

## Not executed here

- Docker/Compose startup and container builds: Docker is not installed on this workstation. Configuration and Dockerfiles are supplied, and `.github/workflows/ci.yml` includes `docker compose up --build --wait` plus HTTP checks.
- Testcontainers mode: the native PostgreSQL fallback was used locally; CI explicitly selects Testcontainers on Ubuntu.
- Production infrastructure, external provider integration, high-load/chaos testing or penetration testing.

Native PostgreSQL uses the actual PostgreSQL binary supplied by the test dependency. It is not H2. Tests reconstruct the engine against durable state; the critical test does not claim to SIGKILL/restart a whole JVM. A whole-process manual scenario is described separately in the recovery runbook.

## Preview cleanup
The temporary native demo and Vite processes were stopped after verification. Reopen them with the documented commands; the demo creates a fresh disposable PostgreSQL database and generated credentials.
