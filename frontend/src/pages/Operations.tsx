import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { Activity, Plus, ShieldCheck } from "lucide-react";
import { api, date, errorMessage, shortId, useApi } from "../api";
import { useAuth } from "../auth";
import type {
  Definition,
  Mode,
  Page,
  Schedule,
  Settings,
  Task,
} from "../types";
import {
  Badge,
  Empty,
  ErrorBox,
  JsonBlock,
  Loading,
  Modal,
  PageHeader,
  Pagination,
} from "../components/ui";
function TaskList() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const result = useApi<Page<Task>>(
    `/tasks?page=${page}${status ? `&status=${status}` : ""}`,
    3000,
  );
  return (
    <>
      <PageHeader
        eyebrow="TASK EXPLORER"
        title="The work behind the workflow."
        description="Inspect individual tasks, retries, and outcomes across all executions."
      />
      <div className="toolbar">
        <span className="muted">All workflow activities</span>
        <select
          aria-label="Filter task status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {[
            "PENDING",
            "RUNNING",
            "COMPLETED",
            "FAILED",
            "RETRY_WAIT",
            "CANCELLED",
          ].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </div>
      <section className="panel">
        <ErrorBox message={result.error} />
        {result.loading ? (
          <Loading />
        ) : result.data?.content.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Execution</th>
                  <th>Status</th>
                  <th>Attempts</th>
                  <th>Last error</th>
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((t) => (
                  <tr key={t.id}>
                    <td>
                      <strong>{t.taskName}</strong>
                      <span className="table-sub">{t.taskType}</span>
                    </td>
                    <td>
                      <Link
                        className="mono text-link"
                        to={`/executions/${t.workflowExecutionId}`}
                      >
                        {shortId(t.workflowExecutionId)}
                      </Link>
                    </td>
                    <td>
                      <Badge status={t.status} />
                    </td>
                    <td className="mono">{t.attemptCount}</td>
                    <td className="error-cell">{t.errorMessage ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="No tasks to show"
            description="Tasks appear here as soon as a workflow execution is created."
          />
        )}
        {result.data && (
          <Pagination
            page={page}
            totalPages={result.data.totalPages}
            total={result.data.totalElements}
            onChange={setPage}
          />
        )}
      </section>
    </>
  );
}
function NewSchedule({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const definitions = useApi<Page<Definition>>(
    "/workflows?size=100&active=true",
  );
  const settings = useApi<Settings>("/settings");
  const [name, setName] = useState("");
  const [definitionId, setDefinitionId] = useState("");
  const [seconds, setSeconds] = useState(3600);
  const [mode, setMode] = useState<Mode>("LOCAL");
  const [input, setInput] = useState("{}");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const parsed = JSON.parse(input);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== "object")
        throw new Error("Input must be a JSON object");
      await api.post("/schedules", {
        name,
        workflowDefinitionId: definitionId,
        intervalSeconds: seconds,
        mode,
        input: parsed,
      });
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Create a schedule" onClose={onClose}>
      <form onSubmit={submit}>
        <label>
          Schedule name
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label>
          Workflow
          <select
            required
            value={definitionId}
            onChange={(e) => setDefinitionId(e.target.value)}
          >
            <option value="">Select an active workflow</option>
            {definitions.data?.content.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} · v{d.version}
              </option>
            ))}
          </select>
        </label>
        <label>
          Run every (seconds)
          <input
            required
            type="number"
            min="60"
            max="31536000"
            value={seconds}
            onChange={(e) => setSeconds(Number(e.target.value))}
          />
        </label>
        <label>
          Execution mode
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value as Mode)}
          >
            <option value="LOCAL">Local</option>
            <option value="KAFKA" disabled={!settings.data?.kafkaEnabled}>
              Kafka
            </option>
          </select>
        </label>
        <label>
          Input JSON
          <textarea
            className="code"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            rows={4}
          />
        </label>
        <p className="field-help">
          First run occurs after one interval. Missed intervals coalesce after
          downtime.
        </p>
        <ErrorBox message={error || definitions.error} />
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="button primary" disabled={busy}>
            Create schedule
          </button>
        </div>
      </form>
    </Modal>
  );
}
function Schedules() {
  const { session } = useAuth();
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const result = useApi<Page<Schedule>>(`/schedules?page=${page}`, 5000);
  async function toggle(s: Schedule) {
    setBusy(s.id);
    try {
      await api.post(`/schedules/${s.id}/${s.enabled ? "disable" : "enable"}`);
      result.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy("");
    }
  }
  return (
    <>
      <PageHeader
        eyebrow="DURABLE SCHEDULES"
        title="Right on time. Every time."
        description="Start recurring workflows with database-backed interval schedules."
        actions={
          session?.role === "ADMIN" && (
            <button className="button primary" onClick={() => setOpen(true)}>
              <Plus size={16} />
              Create schedule
            </button>
          )
        }
      />
      <ErrorBox message={error || result.error} />
      <section className="panel">
        {result.loading ? (
          <Loading />
        ) : result.data?.content.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Schedule</th>
                  <th>Status</th>
                  <th>Interval</th>
                  <th>Next run</th>
                  <th>Mode</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {result.data.content.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <strong>{s.name}</strong>
                      <span className="table-sub mono">
                        {shortId(s.workflowDefinitionId)}
                      </span>
                    </td>
                    <td>
                      <Badge status={s.enabled ? "ACTIVE" : "PAUSED"} />
                    </td>
                    <td>{s.intervalSeconds}s</td>
                    <td>{s.enabled ? date(s.nextRunAt) : "—"}</td>
                    <td>{s.mode}</td>
                    <td>
                      {session?.role === "ADMIN" && (
                        <button
                          className="button secondary small"
                          disabled={busy === s.id}
                          onClick={() => void toggle(s)}
                        >
                          {s.enabled ? "Disable" : "Enable"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="Give your workflow a rhythm"
            description="Create a schedule to run an active workflow at a regular interval."
          />
        )}
        {result.data && (
          <Pagination
            page={page}
            totalPages={result.data.totalPages}
            total={result.data.totalElements}
            onChange={setPage}
          />
        )}
      </section>
      {open && (
        <NewSchedule
          onClose={() => setOpen(false)}
          onSaved={() => {
            setOpen(false);
            result.refresh();
          }}
        />
      )}
    </>
  );
}
function SettingsPage() {
  const settings = useApi<Settings>("/settings");
  const { session } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("USER");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api.post("/auth/users", { username, password, role });
      setNotice(`Account created for ${username}.`);
      setUsername("");
      setPassword("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        eyebrow="WORKSPACE SETTINGS"
        title="Know your engine."
        description="Runtime configuration, execution guarantees, and workspace access."
      />
      <ErrorBox message={settings.error} />
      <div className="two-columns">
        <section className="panel">
          <div className="panel-header">
            <h2>
              <Activity size={17} />
              Runtime configuration
            </h2>
          </div>
          {settings.loading ? (
            <Loading />
          ) : (
            settings.data && (
              <div className="settings-list">
                {Object.entries(settings.data).map(([key, value]) => (
                  <div key={key}>
                    <span>{key.replace(/([A-Z])/g, " $1")}</span>
                    <strong>
                      {typeof value === "boolean"
                        ? value
                          ? "Enabled"
                          : "Disabled"
                        : String(value)}
                    </strong>
                  </div>
                ))}
              </div>
            )
          )}
          <p className="panel-note">
            Runtime values are configured through environment variables. Restart
            the backend after changing them.
          </p>
        </section>
        <section className="panel">
          <div className="panel-header">
            <h2>
              <ShieldCheck size={17} />
              Durability contract
            </h2>
          </div>
          <div className="guarantees">
            <h3>Persisted before progress.</h3>
            <p>
              Each task is leased and every completion is fenced. Completed
              tasks are preserved when the engine restarts.
            </p>
            <h3>Safe retries need safe handlers.</h3>
            <p>
              Task invocation is at least once. External providers must support
              stable idempotency keys and reconciliation after uncertain
              outcomes.
            </p>
            <h3>One shared workspace.</h3>
            <p>
              Admins manage definitions and accounts. Users can observe, start,
              and control executions.
            </p>
          </div>
        </section>
      </div>
      {session?.role === "ADMIN" && (
        <section className="panel account-panel">
          <div className="panel-header">
            <div>
              <h2>Create an account</h2>
              <p>Provision access to this workspace</p>
            </div>
          </div>
          <form onSubmit={create} className="account-form">
            <label>
              Username
              <input
                required
                pattern="[a-zA-Z0-9_.-]{3,80}"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="off"
              />
            </label>
            <label>
              Initial password
              <input
                required
                type="password"
                minLength={12}
                maxLength={72}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
              />
            </label>
            <label>
              Role
              <select value={role} onChange={(e) => setRole(e.target.value)}>
                <option>USER</option>
                <option>ADMIN</option>
              </select>
            </label>
            <button className="button primary" disabled={busy}>
              Create account
            </button>
          </form>
          <ErrorBox message={error} />
          {notice && (
            <p className="success" role="status">
              {notice}
            </p>
          )}
        </section>
      )}
      <details className="settings-raw">
        <summary>Inspect settings response</summary>
        <JsonBlock data={settings.data} />
      </details>
    </>
  );
}
export default function Operations({
  section,
}: {
  section: "tasks" | "schedules" | "settings";
}) {
  return section === "tasks" ? (
    <TaskList />
  ) : section === "schedules" ? (
    <Schedules />
  ) : (
    <SettingsPage />
  );
}
