import { Link } from "react-router-dom";
import { ArrowUpRight, ChevronRight } from "lucide-react";
import { Badge, Empty } from "./ui";
import { date, duration, shortId } from "../api";
import type { Definition, Execution } from "../types";
export default function ExecutionTable({
  runs,
  definitions = [],
}: {
  runs: Execution[];
  definitions?: Definition[];
}) {
  const names = new Map(definitions.map((d) => [d.id, d.name]));
  if (!runs.length)
    return (
      <Empty
        title="No executions yet"
        description="Start a workflow to see every step of its journey here."
        action={
          <Link className="button secondary" to="/workflows">
            Explore workflows
            <ArrowUpRight size={15} />
          </Link>
        }
      />
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Execution / workflow</th>
            <th>Status</th>
            <th>Current task</th>
            <th>Started</th>
            <th>Duration</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.id}>
              <td>
                <Link className="table-title" to={`/executions/${run.id}`}>
                  {names.get(run.workflowDefinitionId) ??
                    `Workflow ${shortId(run.workflowDefinitionId)}`}
                </Link>
                <span className="table-sub mono">
                  {shortId(run.id)} <span>·</span> v{run.workflowVersion}{" "}
                  <span>·</span> {run.mode.toLowerCase()}
                </span>
              </td>
              <td>
                <Badge status={run.status} />
              </td>
              <td className="mono muted">{run.currentTask ?? "—"}</td>
              <td className="muted nowrap">
                {date(run.startedAt ?? run.createdAt)}
              </td>
              <td className="mono muted">
                {run.startedAt
                  ? duration(
                      new Date(run.completedAt ?? new Date()).getTime() -
                        new Date(run.startedAt).getTime(),
                    )
                  : "—"}
              </td>
              <td>
                <Link
                  className="icon-button"
                  aria-label={`View execution ${shortId(run.id)}`}
                  to={`/executions/${run.id}`}
                >
                  <ChevronRight size={17} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
