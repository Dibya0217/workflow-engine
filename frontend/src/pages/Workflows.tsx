import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowUpRight,
  GitBranch,
  MoreHorizontal,
  Play,
  Plus,
  Search,
  ShieldCheck,
} from "lucide-react";
import { api, date, errorMessage, useApi } from "../api";
import { useAuth } from "../auth";
import type { Definition, Execution, Page } from "../types";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  PageHeader,
  Pagination,
  RunModal,
} from "../components/ui";
function WorkflowCard({
  definition,
  onRun,
  onChange,
}: {
  definition: Definition;
  onRun: () => void;
  onChange: () => void;
}) {
  const { session } = useAuth();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const latest = useApi<Page<Execution>>(
    `/executions?workflowDefinitionId=${definition.id}&size=1`,
    6000,
  );
  async function toggle() {
    setBusy(true);
    try {
      await api.post(
        `/workflows/${definition.id}/${definition.active ? "deactivate" : "activate"}`,
      );
      onChange();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <article className="workflow-card">
      <div className="workflow-card-top">
        <span className="workflow-icon">
          <GitBranch size={22} />
        </span>
        <Badge status={definition.active ? "ACTIVE" : "INACTIVE"} />
      </div>
      <Link to={`/workflows/${definition.id}`} className="workflow-name">
        {definition.name}
        <ArrowUpRight size={17} />
      </Link>
      <p>{definition.description || "No description provided."}</p>
      <div className="workflow-mini-flow">
        {definition.specification.tasks.slice(0, 5).map((task, i) => (
          <span key={task.name} title={task.name}>
            {i > 0 && <i />}
            <span className="mini-task">{task.type.slice(0, 3)}</span>
          </span>
        ))}
        {definition.specification.tasks.length > 5 && (
          <small>+{definition.specification.tasks.length - 5}</small>
        )}
      </div>
      <div className="workflow-meta">
        <span>
          v{definition.version}
          <i /> {definition.specification.tasks.length} tasks
        </span>
        <span>
          <ShieldCheck size={12} /> Persisted
        </span>
      </div>
      <div className="workflow-last">
        <span>Last execution</span>
        <span>
          {latest.error
            ? "Unavailable"
            : latest.loading
              ? "Loading…"
              : latest.data?.content[0]
                ? date(latest.data.content[0].createdAt)
                : "Not run yet"}
        </span>
      </div>
      <ErrorBox message={error} />
      <div className="workflow-actions">
        <button
          className="button secondary"
          disabled={!definition.active}
          onClick={onRun}
        >
          <Play size={14} />
          Run workflow
        </button>
        {session?.role === "ADMIN" ? (
          <button
            className="quiet-button"
            disabled={busy}
            onClick={() => void toggle()}
          >
            {definition.active ? "Deactivate" : "Activate"}
          </button>
        ) : (
          <Link
            className="icon-button"
            aria-label="View workflow"
            to={`/workflows/${definition.id}`}
          >
            <MoreHorizontal size={18} />
          </Link>
        )}
      </div>
    </article>
  );
}
export default function Workflows() {
  const { session } = useAuth();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Definition>();
  const navigate = useNavigate();
  const query = `/workflows?page=${page}&size=9&q=${encodeURIComponent(q)}${filter === "all" ? "" : `&active=${filter === "active"}`}`;
  const result = useApi<Page<Definition>>(query);
  return (
    <>
      <PageHeader
        eyebrow="WORKFLOW LIBRARY"
        title="Designed to get things done."
        description="Reusable workflows. Immutable versions. Reliable execution."
        actions={
          session?.role === "ADMIN" && (
            <Link to="/workflows/new" className="button primary">
              <Plus size={17} />
              Create workflow
            </Link>
          )
        }
      />
      <div className="toolbar">
        <div className="tabs">
          {["all", "active", "inactive"].map((value) => (
            <button
              className={value === filter ? "selected" : ""}
              key={value}
              onClick={() => {
                setFilter(value);
                setPage(0);
              }}
            >
              {value === "all" ? "All workflows" : value}
            </button>
          ))}
        </div>
        <div className="search">
          <Search size={16} />
          <input
            aria-label="Search workflows"
            placeholder="Search workflows…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>
      <ErrorBox message={result.error} />
      {result.loading ? (
        <Loading />
      ) : result.data?.content.length ? (
        <div className="workflow-grid">
          {result.data.content.map((d) => (
            <WorkflowCard
              key={d.id}
              definition={d}
              onRun={() => setSelected(d)}
              onChange={result.refresh}
            />
          ))}
        </div>
      ) : (
        <section className="panel">
          <Empty
            title={
              q ? "No matching workflows" : "Your next workflow starts here"
            }
            description={
              q
                ? "Try another name or change the status filter."
                : "Connect a few tasks and give your process a reliable home."
            }
            action={
              session?.role === "ADMIN" && (
                <Link className="button primary" to="/workflows/new">
                  <Plus size={16} />
                  Create workflow
                </Link>
              )
            }
          />
        </section>
      )}
      {result.data && (
        <Pagination
          page={page}
          totalPages={result.data.totalPages}
          total={result.data.totalElements}
          onChange={setPage}
        />
      )}{" "}
      {selected && (
        <RunModal
          definitionId={selected.id}
          name={selected.name}
          onClose={() => setSelected(undefined)}
          onStarted={(run) => navigate(`/executions/${run.id}`)}
        />
      )}
    </>
  );
}
