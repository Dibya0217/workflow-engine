import { Link } from "react-router-dom";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  CheckCheck,
  Clock3,
  GitBranch,
  Plus,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import { useApi, duration } from "../api";
import { useAuth } from "../auth";
import type { Definition, Execution, Page, Stats } from "../types";
import { ErrorBox, Loading, PageHeader } from "../components/ui";
import ExecutionTable from "../components/ExecutionTable";
export default function Dashboard() {
  const stats = useApi<Stats>("/dashboard", 4000);
  const runs = useApi<Page<Execution>>("/executions?size=6", 4000);
  const definitions = useApi<Page<Definition>>("/workflows?size=100");
  const { session } = useAuth();
  const cards = [
    {
      label: "Total workflows",
      value: stats.data?.totalWorkflows,
      icon: GitBranch,
      note: "Versioned and persisted",
      color: "mint",
    },
    {
      label: "Active executions",
      value: stats.data?.activeExecutions,
      icon: Activity,
      note: "Currently in motion",
      color: "blue",
    },
    {
      label: "Completed",
      value: stats.data?.completedExecutions,
      icon: CheckCheck,
      note: "Successfully orchestrated",
      color: "mint",
    },
    {
      label: "Failed executions",
      value: stats.data?.failedExecutions,
      icon: TriangleAlert,
      note: "Ready for inspection",
      color: "orange",
    },
  ];
  const completed = stats.data?.completedExecutions ?? 0;
  const total = stats.data?.totalExecutions ?? 0;
  const percentage = total ? Math.round((completed / total) * 100) : 0;
  return (
    <>
      <PageHeader
        eyebrow="WORKSPACE OVERVIEW"
        title="Keep your workflows in flow."
        description="A clear view of every workflow, every execution, every step."
        actions={
          session?.role === "ADMIN" && (
            <Link className="button primary" to="/workflows/new">
              <Plus size={17} />
              Create workflow
            </Link>
          )
        }
      />
      <ErrorBox message={stats.error} />
      <div className="stats-grid">
        {cards.map(({ label, value, icon: Icon, note, color }) => (
          <section className="stat-card" key={label}>
            <div>
              <span>{label}</span>
              <Icon className={color} size={18} />
            </div>
            <strong>{value ?? "—"}</strong>
            <small>{note}</small>
          </section>
        ))}
      </div>
      <div className="overview-middle">
        <section className="panel health-panel">
          <div className="panel-header">
            <div>
              <span className="section-kicker">EXECUTION HEALTH</span>
              <h2>Progress, at a glance</h2>
            </div>
            <span className="live-label">
              <span className="tiny-dot" /> Updates every 4s
            </span>
          </div>
          <div className="health-body">
            <div
              className="completion-ring"
              style={{
                background: `conic-gradient(var(--accent) 0% ${percentage}%, #242d37 ${percentage}% 100%)`,
              }}
            >
              <div>
                <strong>{total ? `${percentage}%` : "—"}</strong>
                <span>completed</span>
              </div>
            </div>
            <div className="health-breakdown">
              <div>
                <span>
                  <i className="dot mint-bg" />
                  Completed
                </span>
                <strong>{completed}</strong>
              </div>
              <div>
                <span>
                  <i className="dot blue-bg" />
                  In progress
                </span>
                <strong>{stats.data?.activeExecutions ?? "—"}</strong>
              </div>
              <div>
                <span>
                  <i className="dot orange-bg" />
                  Failed
                </span>
                <strong>{stats.data?.failedExecutions ?? "—"}</strong>
              </div>
              <div>
                <span>
                  <Clock3 size={13} />
                  Avg. completion time
                </span>
                <strong>
                  {stats.data ? duration(stats.data.averageDurationMs) : "—"}
                </strong>
              </div>
            </div>
          </div>
        </section>
        <section className="resilience-card">
          <div className="resilience-icon">
            <ShieldCheck size={25} />
          </div>
          <span className="section-kicker">BUILT FOR THE UNEXPECTED</span>
          <h2>
            Interrupted.
            <br />
            Never forgotten.
          </h2>
          <p>
            Durable state, automatic retries, and crash recovery keep your
            workflows moving forward.
          </p>
          <Link to="/settings">
            Explore your engine <ArrowUpRight size={16} />
          </Link>
          <div className="resilience-lines" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        </section>
      </div>
      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Recent executions</h2>
            <p>The latest activity across your workflows</p>
          </div>
          <Link className="text-link" to="/executions">
            View all executions
            <ArrowRight size={15} />
          </Link>
        </div>
        <ErrorBox message={runs.error} />
        {runs.loading ? (
          <Loading />
        ) : (
          runs.data && (
            <ExecutionTable
              runs={runs.data.content}
              definitions={definitions.data?.content}
            />
          )
        )}
      </section>
      <div className="workspace-tip">
        <GitBranch size={17} />
        <span>
          Start small. Build a workflow, configure its retry policy, and watch
          it run.
        </span>
        <Link to="/workflows">
          Your workflows <ArrowRight size={14} />
        </Link>
      </div>
    </>
  );
}
