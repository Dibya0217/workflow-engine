# Database design

PostgreSQL is the source of truth. Flyway migrations live under `src/main/resources/db/migration`. UUID IDs avoid coordination; timestamptz values use UTC; JSONB stores typed definition/input/output documents. Mutable JPA aggregates carry optimistic `row_version`; concurrency-sensitive engine paths additionally use pessimistic execution locks.

| Table | Purpose and invariants |
|---|---|
| workflow_definitions | UNIQUE(name, version), immutable specification JSON, mutable active flag, created_at |
| workflow_executions | FK definition; fixed workflow version; status/current task/input/output; lifecycle timestamps; mode LOCAL/KAFKA; correlation ID; row_version |
| task_executions | FK execution; UNIQUE(execution, task_name), ordinal, type, state, attempt_count, retry_due_at, lease_token/lease_until, inputs/outputs/errors and timestamps; row_version |
| execution_events | Append-only history, FK execution/task, event type/details, timestamp, sequence ID |
| idempotency_records | Primary key stable task business key; successful simulated provider result |
| outbox_events | UUID/event version/type/topic/key/payload, due_at, publisher token/lease, publish attempts, published_at/error; supports multiple relays |
| worker_results | Unique dispatch token with durable result; prevents duplicate result construction |
| app_users | Unique username, BCrypt password hash, ADMIN/USER role |
| workflow_schedules | Definition FK, interval, input, enabled/next_run_at; locked due scheduler creates run and advances time atomically |

Index eligible runs, task leases/due times, history by execution/sequence, and unpublished outbox rows. Foreign keys preserve history. Deletion/retention is intentionally not exposed. Query pagination is bounded; sort columns are allowlisted. Unique workflow versions prevent races; clients resolve conflict by choosing a new version. Schedule ticks coalesce missed occurrences into one run after downtime; no catch-up storm.

## Transaction boundaries
1. API create run: definition lock/activation check + run + tasks + event.
2. Claim: execution lock + current task update + optional command outbox.
3. Complete: execution lock + token validation + task/result + successor/run update + event + optional DLT outbox.
4. Recovery: same execution-before-task lock order + retry/failure event.
5. Business simulation: advisory lock + idempotency result, independent of engine completion.
6. Relay: claim outbox in short transaction; Kafka send outside transaction; token-checked mark after acknowledgement.

All lease and retry decisions use database time to avoid cross-host clock skew. No transaction spans a network call. Replay preserves completed tasks and only resets a failed task's retry budget via an explicit audited action.
