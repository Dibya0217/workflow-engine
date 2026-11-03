CREATE TABLE workflow_definitions (
 id uuid PRIMARY KEY, name varchar(100) NOT NULL, version integer NOT NULL CHECK(version > 0),
 description varchar(2000), specification jsonb NOT NULL, active boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, row_version bigint NOT NULL DEFAULT 0, UNIQUE(name, version)
);
CREATE TABLE workflow_executions (
 id uuid PRIMARY KEY, workflow_definition_id uuid NOT NULL REFERENCES workflow_definitions(id),
 workflow_version integer NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('CREATED','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLING','CANCELLED')),
 mode varchar(10) NOT NULL CHECK(mode IN ('LOCAL','KAFKA')), current_task varchar(80),
 input_data jsonb NOT NULL, output_data jsonb NOT NULL, correlation_id varchar(100) NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, started_at timestamptz, completed_at timestamptz,
 row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_execution_status_created ON workflow_executions(status, created_at);
CREATE INDEX ix_execution_definition ON workflow_executions(workflow_definition_id, created_at DESC);
CREATE TABLE task_executions (
 id uuid PRIMARY KEY, workflow_execution_id uuid NOT NULL REFERENCES workflow_executions(id),
 task_name varchar(80) NOT NULL, task_type varchar(80) NOT NULL, position integer NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED','RETRY_WAIT','SKIPPED','CANCELLED')),
 attempt_count integer NOT NULL DEFAULT 0, retry_base integer NOT NULL DEFAULT 0,
 input_data jsonb NOT NULL, output_data jsonb NOT NULL, error_message text,
 retry_due_at timestamptz, lease_token uuid, lease_until timestamptz,
 started_at timestamptz, completed_at timestamptz, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 row_version bigint NOT NULL DEFAULT 0, UNIQUE(workflow_execution_id,task_name), UNIQUE(workflow_execution_id, position)
);
CREATE INDEX ix_task_lease ON task_executions(lease_until) WHERE status='RUNNING';
CREATE INDEX ix_task_due ON task_executions(retry_due_at) WHERE status='RETRY_WAIT';
CREATE TABLE execution_events (
 id bigserial PRIMARY KEY, workflow_execution_id uuid NOT NULL REFERENCES workflow_executions(id),
 task_execution_id uuid REFERENCES task_executions(id), event_type varchar(80) NOT NULL,
 details jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_event_execution ON execution_events(workflow_execution_id, id);
CREATE TABLE idempotency_records (
 idempotency_key varchar(200) PRIMARY KEY, task_type varchar(80) NOT NULL,
 result jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY, event_version integer NOT NULL DEFAULT 1, event_type varchar(80) NOT NULL,
 topic varchar(200) NOT NULL, message_key varchar(200) NOT NULL, payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), due_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 publish_attempts integer NOT NULL DEFAULT 0, lease_token uuid, lease_until timestamptz,
 published_at timestamptz, last_error text
);
CREATE INDEX ix_outbox_pending ON outbox_events(due_at,created_at) WHERE published_at IS NULL;
CREATE TABLE worker_results (
 dispatch_token uuid PRIMARY KEY, result jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE app_users (
 id uuid PRIMARY KEY, username varchar(80) NOT NULL UNIQUE, password_hash varchar(100) NOT NULL,
 role varchar(10) NOT NULL CHECK(role IN ('ADMIN','USER')), created_at timestamptz NOT NULL,
 row_version bigint NOT NULL DEFAULT 0
);
CREATE TABLE workflow_schedules (
 id uuid PRIMARY KEY, workflow_definition_id uuid NOT NULL REFERENCES workflow_definitions(id), name varchar(100) NOT NULL,
 interval_seconds bigint NOT NULL CHECK(interval_seconds >= 60), enabled boolean NOT NULL,
 mode varchar(10) NOT NULL CHECK(mode IN ('LOCAL','KAFKA')), input_data jsonb NOT NULL,
 next_run_at timestamptz NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_schedule_due ON workflow_schedules(next_run_at) WHERE enabled;
