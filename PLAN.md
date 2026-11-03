# FlowLab implementation plan

## Objective and acceptance
Build an understandable Java 21 / Spring Boot 3 modular monolith with durable sequential workflows, PostgreSQL state, retries and recovery, optional Kafka workers, JWT roles, and a React console backed by actual APIs. No exactly-once claim for arbitrary external side effects. PostgreSQL is the coordination authority; Redis is optional infrastructure, not a second lock authority.

## Initial inspection
Repository contained a Java hello-world class and bare Maven descriptor. No project-specific agent instructions. Preserve existing IDE changes. Shell has no Java/Maven/Node/Docker on PATH; investigate bundled runtimes. Docker-dependent checks must be reported honestly.

## Phases (each milestone compiles/tests applicable code)
- [x] 1. Project setup, domain records, explicit state machines.
- [x] 2. Immutable workflow versions, sequential graph validation, activation.
- [x] 3. Task handlers, registry, core execution coordination.
- [x] 4. PostgreSQL/Flyway persistence, transactions, DTOs.
- [x] 5. Database-backed exponential retries and permanent failures.
- [x] 6. Expiring leases, fencing, crash recovery and concurrent claims.
- [x] 7. REST management APIs, pagination/filtering, OpenAPI, errors.
- [x] 8. Unit and PostgreSQL integration/recovery tests.
- [x] 9. Kafka commands/results, transactional outbox, duplicates and DLT.
- [x] 10. React dashboard, workflow designer and live execution history.
- [x] 11. JWT/roles, observability, schedules and settings.
- [x] 12. Docker Compose/CI files, runbook, interview guide and final checks, including Docker runtime verification below.

## Architecture review before implementation
- One deployable backend, packages by responsibility; domain has no infrastructure dependencies.
- Claim/update transactions are short. Handler invocation and Kafka send occur outside engine row locks.
- Lock workflow first, then task, consistently. Claims use PostgreSQL SKIP LOCKED; completion requires the current lease token.
- Completing a task, advancing the execution and recording history share one transaction.
- Kafka command creation shares the claim transaction; publication is at least once. Result creation is durably deduplicated by dispatch token.
- Expired attempts can overlap external work: stable business idempotency keys are required; leases alone cannot prevent duplicated external effects.
- Pause gates new claims; cancellation fences any in-flight completion. Neither reverses external effects.
- Definition contents are immutable. PUT creates a new version; only activation is mutable.
- Bounded worker capacity and per-attempt deadlines prevent unbounded queues. Retry due times survive restart.
- Every failure path must either persist a retry/failure or leave a recoverable expired lease.

## Verification strategy
Pure tests for definition graphs/state transitions/backoff. Real PostgreSQL tests for claims, persistence, fencing, retries, idempotency, cancellation, recovery after engine reconstruction, schedules and outbox. Kafka broker integration for command/result and duplicate delivery. Testcontainers profile for Docker environments; embedded native PostgreSQL/broker where Docker is unavailable. Frontend typecheck/build and browser verification if supported. Record executed results separately from unexecuted checks.

## Verified checkpoints
- 2026-11-02: first compile and 11 unit tests passed.
- 2026-11-02: Java 21 `mvn verify` passed: 11 unit + 17 integration tests, real native PostgreSQL and embedded Kafka. Includes reconstructed-engine crash recovery, duplicate commands/results, concurrent claims, provider idempotency, retries and JWT role authorization.
- 2026-11-02: expanded backend verification passed: 11 unit + 23 integration tests. Added outbox failure/rollback/fencing, schedule concurrency, poison-message DLT and nested JSONB regression coverage.
- 2026-11-03: frontend TypeScript/Vite production build and all 6 graph tests passed. Browser creation → validation → save → activation → execution succeeded; validation ran once and payment retried once before completion. Desktop (1440px) and mobile (390px) layouts verified without document overflow.
- 2026-11-03: screenshots, complete README, recovery runbook, dependency notes and 30-question interview guide delivered.

## Docker acceptance verification
- [x] 2026-11-03: GitHub Actions Compose startup/build and HTTP smoke checks passed. Local Testcontainers mode passed all 34 backend tests with Docker Engine 29.9.2 after upgrading Testcontainers to 1.21.4 for current Docker API compatibility. See the verification record for the CI job link and failure diagnosis.

See `docs/VERIFICATION.md` for executed evidence and explicit limits.
