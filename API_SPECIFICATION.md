# REST API v1

Base `/api/v1`; JSON; bearer JWT; timestamps ISO-8601 UTC. Interactive schema `/swagger-ui/index.html`, machine schema `/v3/api-docs`. API errors use RFC 9457 ProblemDetail with safe messages and correlation ID. Lists return `{content, page, size, totalElements, totalPages}`. `page` is zero-based, `size` 1–100, `sort` is an allowlisted field and `direction` asc/desc.

| Method | Path | Role / behavior |
|---|---|---|
| POST | /auth/login | public; username/password → accessToken, expiresIn, username, role |
| POST | /auth/users | ADMIN; provision username/password/role |
| GET | /auth/me | authenticated identity |
| POST | /workflows | ADMIN; name/version/description/tasks; initially inactive |
| GET | /workflows | authenticated; q and active filters |
| GET | /workflows/{id} | definition and specification |
| PUT | /workflows/{id} | ADMIN; create new immutable version of same name, old runs unchanged |
| POST | /workflows/{id}/activate or deactivate | ADMIN |
| POST | /workflows/validate | ADMIN; graph/handler/retry validation |
| GET | /task-types | registered task types |
| POST | /workflows/{id}/executions | USER/ADMIN; `{input: {}, mode: "LOCAL" or "KAFKA"}` → 201 run |
| GET | /executions | status, workflowDefinitionId filters |
| GET | /executions/{id} | run detail |
| GET | /executions/{id}/tasks | ordered persisted task records |
| GET | /executions/{id}/history | paginated chronological events |
| POST | /executions/{id}/pause, resume, retry, cancel | explicit lifecycle controls; invalid transition → 409 |
| GET | /tasks | paginated tasks, optional status filter |
| GET | /dashboard | database-derived counts and duration |
| GET/POST | /schedules | list / ADMIN creates interval schedule |
| POST | /schedules/{id}/enable or disable | ADMIN |
| GET | /settings | authenticated, non-secret engine capabilities |

Definition task shape: `{name,type,next,retryPolicy:{maxAttempts,initialDelayMs,backoffMultiplier,maxDelayMs},config:{}}`. First array task is the entry; every task must occur once in a single acyclic chain and every type must be registered. `maxAttempts` counts the first attempt. Manual retry starts a new retry budget but retains lifetime attempt_count. Modes default LOCAL; KAFKA is rejected if messaging is disabled. No remote invocation occurs on the HTTP request thread.

Errors: 400 invalid request/graph, 401 invalid login/token, 403 role denied, 404 missing resource, 409 duplicate version/illegal state, 503 unavailable infrastructure. Secrets and password hashes never appear in DTOs.
