import { useState } from "react";
import { RefreshCw } from "lucide-react";
import { useApi } from "../api";
import type { Definition, Execution, Page } from "../types";
import { ErrorBox, Loading, PageHeader, Pagination } from "../components/ui";
import ExecutionTable from "../components/ExecutionTable";
export default function Executions() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [workflow, setWorkflow] = useState("");
  const runs = useApi<Page<Execution>>(
    `/executions?page=${page}&size=15${status ? `&status=${status}` : ""}${workflow ? `&workflowDefinitionId=${workflow}` : ""}`,
    3000,
  );
  const definitions = useApi<Page<Definition>>("/workflows?size=100");
  return (
    <>
      <PageHeader
        eyebrow="EXECUTION MONITOR"
        title="Every run tells a story."
        description="Follow active work, inspect failures, and trace every transition."
        actions={
          <button className="button secondary" onClick={runs.refresh}>
            <RefreshCw size={16} />
            Refresh
          </button>
        }
      />
      <div className="toolbar">
        <div className="tabs">
          {["", "RUNNING", "COMPLETED", "FAILED"].map((s) => (
            <button
              className={s === status ? "selected" : ""}
              key={s}
              onClick={() => {
                setStatus(s);
                setPage(0);
              }}
            >
              {s ? s.toLowerCase() : "All executions"}
            </button>
          ))}
        </div>
        <div className="filter-group">
          <select
            aria-label="Filter execution status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {[
              "CREATED",
              "RUNNING",
              "PAUSED",
              "COMPLETED",
              "FAILED",
              "CANCELLED",
            ].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
          <select
            aria-label="Filter by workflow"
            value={workflow}
            onChange={(e) => {
              setWorkflow(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All workflows</option>
            {definitions.data?.content.map((d) => (
              <option value={d.id} key={d.id}>
                {d.name} · v{d.version}
              </option>
            ))}
          </select>
        </div>
      </div>
      <section className="panel">
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
        )}{" "}
        {runs.data && (
          <Pagination
            page={page}
            totalPages={runs.data.totalPages}
            total={runs.data.totalElements}
            onChange={setPage}
          />
        )}
      </section>
    </>
  );
}
