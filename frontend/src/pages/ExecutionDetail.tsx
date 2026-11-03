import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Check,
  Clock3,
  Pause,
  Play,
  RotateCcw,
  X,
} from "lucide-react";
import { api, date, duration, errorMessage, shortId, useApi } from "../api";
import type { Definition, Event, Execution, Page, Task } from "../types";
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
export default function ExecutionDetail() {
  const { id } = useParams();
  const run = useApi<Execution>(`/executions/${id}`, 2000);
  const tasks = useApi<Task[]>(`/executions/${id}/tasks`, 2000);
  const [eventPage, setEventPage] = useState(0);
  const history = useApi<Page<Event>>(
    `/executions/${id}/history?page=${eventPage}&size=20`,
    2000,
  );
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [cancel, setCancel] = useState(false);
  const [tab, setTab] = useState("Timeline");
  const [selected, setSelected] = useState<string>();
  const definitions = useApi<Page<Definition>>("/workflows?size=100");
  async function action(name: string) {
    setBusy(true);
    setError("");
    try {
      await api.post(`/executions/${id}/${name}`);
      run.refresh();
      tasks.refresh();
      history.refresh();
      setCancel(false);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const r = run.data;
  if (!r)
    return (
      <>
        <ErrorBox message={run.error} />
        {run.loading && <Loading />}
      </>
    );
  const current = tasks.data?.find((t) => t.id === selected);
  const name =
    definitions.data?.content.find((d) => d.id === r.workflowDefinitionId)
      ?.name ?? `Workflow ${shortId(r.workflowDefinitionId)}`;
  return (
    <>
      <Link to="/executions" className="back-link">
        <ArrowLeft size={14} />
        All executions
      </Link>
      <PageHeader
        eyebrow={`EXECUTION / ${shortId(r.id).toUpperCase()}`}
        title={name}
        description={`Version ${r.workflowVersion} · ${r.mode === "KAFKA" ? "Kafka worker" : "Local worker"} · Started ${date(r.createdAt)}`}
        actions={
          <>
            {r.status === "RUNNING" && (
              <button
                disabled={busy}
                className="button secondary"
                onClick={() => void action("pause")}
              >
                <Pause size={15} />
                Pause
              </button>
            )}
            {r.status === "PAUSED" && (
              <button
                disabled={busy}
                className="button primary"
                onClick={() => void action("resume")}
              >
                <Play size={15} />
                Resume
              </button>
            )}
            {r.status === "FAILED" && (
              <button
                disabled={busy}
                className="button primary"
                onClick={() => void action("retry")}
              >
                <RotateCcw size={15} />
                Retry failed task
              </button>
            )}
            {["CREATED", "RUNNING", "PAUSED", "FAILED"].includes(r.status) && (
              <button className="button danger" onClick={() => setCancel(true)}>
                <X size={15} />
                Cancel
              </button>
            )}
          </>
        }
      />
      <ErrorBox message={error || run.error || tasks.error} />
      <div className="execution-summary">
        <div>
          <span>Status</span>
          <Badge status={r.status} />
        </div>
        <div>
          <span>Current task</span>
          <strong className="mono">
            {r.currentTask ?? "All steps complete"}
          </strong>
        </div>
        <div>
          <span>Elapsed time</span>
          <strong>
            {r.startedAt
              ? duration(
                  new Date(r.completedAt ?? new Date()).getTime() -
                    new Date(r.startedAt).getTime(),
                )
              : "Not started"}
          </strong>
        </div>
        <div>
          <span>Task progress</span>
          <strong>
            {tasks.data?.filter((t) => t.status === "COMPLETED").length ?? 0}{" "}
            <span className="muted">/ {tasks.data?.length ?? 0} completed</span>
          </strong>
        </div>
      </div>
      <div className="tabs detail-tabs">
        {["Timeline", "Event history", "Input & output"].map((t) => (
          <button
            key={t}
            className={tab === t ? "selected" : ""}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === "Timeline" && (
        <div className="execution-layout">
          <section className="panel timeline-panel">
            <div className="panel-header">
              <div>
                <h2>Task timeline</h2>
                <p>Persisted state, in execution order</p>
              </div>
              <span className="live-label">
                <span className="tiny-dot" /> Live
              </span>
            </div>
            <div className="timeline">
              {tasks.data?.map((t, i) => (
                <button
                  key={t.id}
                  className={`timeline-item ${selected === t.id ? "selected" : ""}`}
                  onClick={() => setSelected(t.id)}
                >
                  <div className={`timeline-number ${t.status.toLowerCase()}`}>
                    {t.status === "COMPLETED" ? <Check size={17} /> : i + 1}
                  </div>
                  <div className="timeline-content">
                    <div>
                      <strong>{t.taskName}</strong>
                      <Badge status={t.status} />
                    </div>
                    <p>
                      {t.taskType} <span>·</span> {t.attemptCount} attempt
                      {t.attemptCount !== 1 ? "s" : ""}
                    </p>
                    {t.errorMessage && (
                      <div className="inline-error">{t.errorMessage}</div>
                    )}
                    {t.retryDueAt && t.status === "RETRY_WAIT" && (
                      <small>Next attempt: {date(t.retryDueAt)}</small>
                    )}
                  </div>
                </button>
              ))}
            </div>
          </section>
          <section className="panel task-inspector">
            {current ? (
              <>
                <div className="panel-header">
                  <h2>{current.taskName}</h2>
                  <Badge status={current.status} />
                </div>
                <div className="inspector-body">
                  <dl>
                    <dt>Started</dt>
                    <dd>{date(current.startedAt)}</dd>
                    <dt>Completed</dt>
                    <dd>{date(current.completedAt)}</dd>
                    <dt>Lease expires</dt>
                    <dd>{date(current.leaseUntil)}</dd>
                    <dt>Attempts</dt>
                    <dd>{current.attemptCount}</dd>
                  </dl>
                  {current.errorMessage && (
                    <ErrorBox message={current.errorMessage} />
                  )}
                  <h3>Task output</h3>
                  <JsonBlock data={current.outputData} />
                </div>
              </>
            ) : (
              <Empty
                title="A closer look at every step"
                description="Select a task to inspect its result, timing, and retry details."
              />
            )}
          </section>
        </div>
      )}
      {tab === "Event history" && (
        <section className="panel">
          <div className="panel-header">
            <h2>Execution events</h2>
            <span className="muted">Append-only audit trail</span>
          </div>
          <ErrorBox message={history.error} />
          <div className="event-list">
            {history.data?.content.map((event) => (
              <div key={event.id}>
                <Clock3 size={15} />
                <div>
                  <strong>
                    {event.eventType.replaceAll("_", " ").toLowerCase()}
                  </strong>
                  <p className="mono">{JSON.stringify(event.details)}</p>
                </div>
                <time>{date(event.createdAt)}</time>
              </div>
            ))}
          </div>
          {history.data && (
            <Pagination
              page={eventPage}
              totalPages={history.data.totalPages}
              total={history.data.totalElements}
              onChange={setEventPage}
            />
          )}
        </section>
      )}
      {tab === "Input & output" && (
        <div className="two-columns">
          <section className="panel">
            <div className="panel-header">
              <h2>Workflow input</h2>
            </div>
            <JsonBlock data={r.inputData} />
          </section>
          <section className="panel">
            <div className="panel-header">
              <h2>Accumulated output</h2>
            </div>
            <JsonBlock data={r.outputData} />
          </section>
        </div>
      )}
      <p className="correlation">
        Correlation ID <code>{r.correlationId}</code>
      </p>
      {cancel && (
        <Modal title="Cancel this execution?" onClose={() => setCancel(false)}>
          <p className="muted">
            Pending work will be cancelled. A task already calling an external
            service may still finish its side effect; cancellation cannot
            reverse it.
          </p>
          <div className="modal-actions">
            <button
              className="button secondary"
              onClick={() => setCancel(false)}
            >
              Keep running
            </button>
            <button
              className="button danger"
              disabled={busy}
              onClick={() => void action("cancel")}
            >
              Cancel execution
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
