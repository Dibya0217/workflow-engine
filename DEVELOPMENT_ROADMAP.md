# Development roadmap

1. Establish build, pure domain and validation; compile/test.
2. Add immutable definitions; validate invalid graphs and version behavior.
3. Define handler boundary and explicit lifecycle coordination; compile/test pure mechanics.
4. Add Flyway/JPA, transactional engine and PostgreSQL checks.
5. Persist retry deadlines, classify failures, cap backoff.
6. Add expiring leases and recovery with stale completion/concurrency regression tests.
7. Expose DTO-based management API, list filters, errors and OpenAPI.
8. Validate on real PostgreSQL, including reconstructed-engine recovery and idempotent effects.
9. Add outbox and Kafka broker integration; verify duplicates and publication failure windows.
10. Build dark React console with real API queries and sequential visual designer.
11. Complete JWT/roles, operational metrics, durable interval schedules.
12. Package Docker/Compose/CI, comprehensive runbook and interview questions; run final verification.

Future extensions: heartbeat/checkpoint protocol for long individual activities, parallel/branching DAGs, saga compensation, human approval tasks, per-tenant authorization, provider reconciliation adapters, retention/archival, cron/calendar schedules, replay tooling, and horizontal worker routing by task type. These are not represented as current features.
