# FlowLab interview preparation

## The two-minute explanation

“I built a small workflow engine to learn durable orchestration, not another CRUD wrapper. A workflow is an immutable, validated sequence of task definitions. Starting it creates persisted task instances. A scheduler claims the current task with a PostgreSQL row lock and an expiring lease. The handler runs outside the transaction. Completion checks the current lease token and atomically persists the result and advances the workflow. If the application dies, recovery scans expired leases and retries only the incomplete task. Kafka execution uses a transactional outbox for commands and results. Stable provider idempotency keys handle the gap between a side effect and saving its result. The React console shows real persisted history. The most valuable test simulates losing the result after a provider commits and proves recovery does not repeat the earlier completed task or create a second simulated payment.”

## Architecture and decisions worth explaining

**Modular monolith:** One deployable Spring Boot application with packages for definition, execution, engine, handlers, persistence, retry, recovery, scheduling, messaging, security and observability. This keeps local transactions straightforward and avoids service discovery and distributed transaction overhead before they are justified.

**Immutable versions:** Old executions always reference their original specification. Editing creates a new version with a new ID. Activation is mutable because it gates new starts without altering the specification.

**PostgreSQL owns coordination:** `SKIP LOCKED` allows independent executions to progress concurrently. Every mutation of a run locks its execution row first. A lease allows work outside a transaction, while fencing prevents obsolete owners from committing. Redis is not needed for this correctness path.

**Two transaction boundaries:** Engine claim/completion and provider side effects are separate. Holding the engine transaction while calling a provider would not make the provider transactional and would increase contention. The simulated provider uses its own atomic idempotency record; a real provider needs an explicit API contract.

**Durable retries:** Store a due timestamp, not a sleeping Java thread. Retry decisions survive restart and are observable. Manual retry retains lifetime attempts while resetting the current budget.

**Outbox over “DB then send”:** State and message intent commit together. Publication is at least once because marking the acknowledgement is a separate database operation. The architecture embraces duplicates and explicitly handles them.

## Why a custom engine rather than Camunda?

For education and a constrained portfolio project, implementing the durability mechanisms makes their tradeoffs visible. Camunda or another established engine would usually be a stronger production choice for BPMN, human tasks, migrations, compensation, mature operations, and support. FlowLab is not a claim that rebuilding these features is cheaper. Explain the limited sequential scope honestly.

## Saga versus workflow orchestration

A workflow engine coordinates persisted steps and their state over time. A saga is a consistency pattern for a sequence of local transactions with compensating actions when later work fails. A workflow engine can execute a saga, but orchestration alone does not supply compensation. FlowLab supports sequencing/retry/recovery and does **not** implement automatic refund or inventory compensation. Repeating a task and undoing a task are different operations.

## Consistency and recovery examples

- **Before claim commit:** Nothing was claimed; the next scheduler can proceed.
- **After provider commit, before engine completion:** The provider may have acted. Reuse its idempotency key or look up the outcome; a lease cannot undo the effect.
- **Old worker returns after lease recovery:** Its token fails the completion check, so persisted state cannot move twice.
- **Broker receives a command, relay crashes before marking it:** The outbox resends. Consumers and handlers must tolerate duplicates.
- **Cancellation during an external call:** FlowLab fences the result but cannot guarantee the provider stopped. Reconciliation/compensation is a separate concern.
- **Pause during the last task:** That task may finish and the run may complete; pause gates new work, not already accepted work.

## Scaling path

Measure contention and queue delay first. Scale backend replicas with independent worker capacity while keeping one lock authority. Split Kafka workers into deployments by task type only when resource or ownership isolation is useful. Add partition/routing strategy, heartbeat/checkpoint support, provider timeouts, per-tenant authorization, retention, outbox partitioning, and backlog metrics. Do not cache mutable execution ownership in Redis. Add branches/joins only after defining their persisted token/merge semantics.

## Thirty questions and answers

1. **What does “durable workflow” mean?** Its definition and execution decisions are persisted so process memory loss does not erase progress. Durability still depends on the database's storage, backup and availability guarantees.

2. **Where is the source of truth?** PostgreSQL. Kafka transports commands/results, while database rows define which task and lease are current. The dashboard reads APIs over that state.

3. **Why separate a workflow definition from an execution?** A definition is reusable versioned logic. An execution is one input, lifecycle, set of task instances and history. Mixing them makes version changes corrupt in-flight meaning.

4. **How do you validate a sequential workflow?** Require unique task names and known handler types, walk from the first task, reject missing targets and repeated nodes, and require the walk to cover every task. Validate retry bounds as well.

5. **How do concurrent workers avoid owning the same task?** Select an eligible execution `FOR UPDATE SKIP LOCKED`, check its persisted current task, assign a token/deadline, and commit. All completion/control paths lock the same execution aggregate.

6. **What is a lease?** A bounded right to attempt work, represented by a persisted token and expiry. It permits recovery when the owner disappears. Expiration is evidence of uncertainty, not proof that an external call stopped.

7. **What is fencing?** Rejecting an update from an obsolete owner. FlowLab verifies the current task status, execution ID, lease token and deadline before committing the result.

8. **Why are locks alone insufficient?** A database row lock cannot safely span an arbitrarily long external call, and it disappears when the transaction ends. A lease plus token fencing spans the invocation interval without holding that lock.

9. **What happens after a crash?** The scheduler continues pending/due work. Recovery finds expired RUNNING tasks, persists retry/failure state, and lets the scheduler claim a fresh attempt. Completed tasks remain excluded.

10. **Why not claim exactly-once execution?** The database and a remote provider cannot generally commit atomically. If the provider succeeds and the response is lost, another invocation may occur. Idempotency and reconciliation make this safe where the provider supports them.

11. **How is the idempotency key chosen?** Execution UUID plus task name. It is stable across retry/recovery and distinct across new executions. An attempt token would be wrong for deduplicating a business effect because it changes on every retry.

12. **What does the simulated provider prove?** Concurrent calls with the same key return one persisted result. It demonstrates the local idempotency contract, not universal exactly-once semantics for a real payment API.

13. **What if a provider has no idempotency API?** Add a business uniqueness constraint/reconciliation endpoint if possible, or accept/document duplicate risk and use compensation or manual review. A local “already sent” flag cannot close the uncertain-outcome window by itself.

14. **How does retry backoff work?** Delay grows exponentially from the initial delay and is capped. The due timestamp is persisted. Attempts include the first call; only retryable failures and expired leases within budget are rescheduled.

15. **Why not `Thread.sleep()`?** Sleeping consumes process resources and loses the retry schedule on restart. A due timestamp supports restart, many waiting workflows, and multiple scheduler replicas.

16. **How are permanent errors handled?** Mark the task and workflow failed, retain the error and history, and stop progression. KAFKA mode also emits a permanent-failure DLT event. An operator can inspect and explicitly retry after remediation.

17. **How does manual retry differ from automatic retry?** It is an audited operator action allowed for FAILED runs. It resets the current task's retry budget, preserves lifetime attempt history, and leaves earlier completed tasks unchanged.

18. **What is the transactional outbox?** A table that records a message in the same transaction as its related state. A separate relay publishes and records acknowledgement. It prevents losing a command in the “DB succeeded, send failed” window.

19. **Can the outbox publish duplicates?** Yes: a crash after broker acknowledgement but before marking published causes resend. Marking before send would create loss instead. Consumers must deduplicate or fence duplicates.

20. **How are duplicate Kafka results handled?** Workers deduplicate persisted result creation by dispatch token; the engine independently accepts only the current RUNNING task/token before advancing. A duplicate completed result is ignored.

21. **What is a dead-letter topic?** An inspection path for records that cannot be processed safely after bounded delivery attempts. FlowLab sends poison envelopes and permanent business failures there; it does not implement automatic replay or hide them as successful work.

22. **Does Kafka exactly-once processing solve the database/provider problem?** No. Kafka transactions can coordinate Kafka records and offsets under their guarantees, but they do not atomically commit arbitrary PostgreSQL writes or remote provider effects without additional protocols.

23. **Why both pessimistic and optimistic locking?** Pessimistic aggregate locking serializes state-machine decisions and claims. JPA version columns detect stale updates on mutable entities in other paths. Neither replaces external idempotency.

24. **How does cancellation work?** It records CANCELLING then CANCELLED atomically, cancels eligible task rows and clears their leases. A later result cannot advance the run. It does not undo previously completed or still-running external effects.

25. **How do long-running workflows work?** They consist of many short activities and persisted waits/retry deadlines, so total duration need not fit a process lifetime. Individual long activities require heartbeat/checkpoint extensions; FlowLab currently uses bounded lease deadlines.

26. **How do you test crashes deterministically?** Commit earlier task state, invoke the simulated provider without committing its engine result, expire the lease in SQL, reconstruct the coordinator, then verify stale-result rejection, recovered completion, original output reuse and no extra completed-task attempt.

27. **Why real PostgreSQL rather than only H2?** PostgreSQL JSONB, advisory locks, `SKIP LOCKED`, timestamptz, SQL functions and transaction semantics are part of the implementation. In-memory approximations would miss the important failure paths.

28. **How is access controlled?** BCrypt verifies passwords, a signed short-lived JWT identifies the user and roles, and Spring Security gates APIs. ADMIN manages definitions/schedules/users; authenticated users manage executions. Tokens are held in browser memory, not persistent local storage.

29. **What are the observability signals?** Correlation IDs connect request and worker logs, persisted events expose lifecycle history, and counters/timers measure retries, failures, lease recovery, stale results and outbox health. Persisted events are the audit record; metrics can be approximate.

30. **What would you improve before production?** Add provider-specific reconciliation, heartbeats/checkpoints, TLS and broker authentication, token lifecycle controls and rate limits, tenant isolation where needed, retention/backups, backlog alerts, replicated infrastructure, capacity/load tests, failure injection and operational DLT tooling. Choose a mature engine when those needs outweigh the educational value of custom code.
