# Recovery and failure runbook

## Guarantees
- Every task claim is committed before invocation. One current lease owns the right to commit its result.
- Execution updates serialize on the workflow execution row. An old lease cannot advance the workflow after recovery or cancellation.
- Completed tasks are never selected by the scheduler. Retry only selects the incomplete current task.
- Provider invocation is **at least once**. A crash can occur after the provider commits but before FlowLab commits the result. Exactly-once external side effects are not guaranteed by a local SQL transaction, Kafka offset, or lease.
- Simulated providers persist their result under `executionId:taskName`, atomically with their simulated effect. A real provider must accept that same stable key and support result lookup/reconciliation.
- Workflow versions are immutable; activation only controls new runs. Deactivation does not interrupt existing executions.

## Failure windows
| Failure | Durable evidence | Recovery |
|---|---|---|
| Before claim commit | Task still pending | Any scheduler claims it |
| After claim, before handler | RUNNING with lease | On expiry, persist retry due time or fail at budget limit |
| During external request | Unknown provider outcome | Reconcile by idempotency key before repeating effect |
| After provider commit, before completion | Provider key has result, task still RUNNING | Retry reuses result and completes under new token |
| After task completion commit | Task COMPLETED and successor persisted | Continue successor; do not repeat completed task |
| DB commit, broker unavailable | Unpublished outbox row | Relay persists backoff and retries |
| Broker ack, relay crashes before mark | Outbox still pending | Duplicate send; consumer deduplication/fencing |
| Worker result commit, Kafka unavailable | Result + outbox committed together | Result eventually publishes |
| Late worker after recovery | Stale token | Ignore result; record stale-result metric |
| Two recovery replicas | Execution row lock / SKIP LOCKED | One wins, others skip |
| Scheduler downtime | Persisted due timestamp | Run once after restart and coalesce missed intervals |
| Poison message | Consumer bounded attempts | DLT publish with acknowledgement before offset progress |

## Reproduce a crash locally
1. Start Compose and create the workflow in `examples/order-processing.json`. Its payment handler intentionally fails its first attempt.
2. Activate it and start a LOCAL run with `{"input":{"orderId":"CRASH-DEMO"}}`.
3. Use a handler with an external test barrier to hold an in-flight attempt, then `docker compose kill -s SIGKILL backend`. For a quick demonstration, kill during the persisted retry wait; that exercises durable retries but is not equivalent to the in-flight failure window.
4. `docker compose up -d backend`. Recovery waits for the persisted lease deadline; it never immediately steals a non-expired lease.
5. Inspect execution history for `TASK_LEASE_EXPIRED`, `TASK_RETRY_SCHEDULED`, and subsequent completion.
6. The deterministic automated `EngineIT.reconstructsEngineAfterInterruptionWithoutRepeatingCompletedTask` specifically covers the harder provider-committed / result-lost window by reconstructing the engine against the same real database and advancing persisted deadlines.

## Controls
Pause is cooperative: in-flight work may complete, but no successor is claimed until resume. Cancel fences completion and cancels pending work; it cannot undo external effects. Manual retry of FAILED resets that task's retry budget while retaining lifetime attempts and history. It does not re-run preceding completed tasks.

## Limits and operating actions
- Individual activities must fit within TASK_LEASE_SECONDS; no heartbeats/checkpoints yet. External clients need bounded network timeouts. A stuck handler can occupy local worker capacity until interrupted/restarted; lease recovery cannot force an arbitrary provider request to stop.
- Retry limits include expired attempts. After exhaustion, investigate the provider outcome before manual retry.
- FlowLab has no automatic compensation or DLT replay API. Inspect DLT records, fix malformed producers, reconcile provider state, then retry the workflow through its API. Do not blindly republish permanent failures.
- Do not delete volumes to restart the engine. `docker compose down -v` deletes durable history and is for disposable test environments only.
- Keep database and Kafka backups/retention policies in real deployments. Outbox/history retention is not automated here.
