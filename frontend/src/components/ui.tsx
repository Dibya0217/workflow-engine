import { useEffect, useRef, useState } from "react";
import type { ReactNode, FormEvent } from "react";
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  LoaderCircle,
  X,
  Play,
} from "lucide-react";
import { api, errorMessage, useApi } from "../api";
import type { Execution, Mode, Settings, Status } from "../types";
export function Badge({ status }: { status: Status | string }) {
  return (
    <span className={`badge ${status.toLowerCase()}`}>
      <i />
      {status.replaceAll("_", " ").toLowerCase()}
    </span>
  );
}
export function ErrorBox({ message }: { message: string }) {
  return message ? (
    <div className="error" role="alert">
      <AlertCircle size={17} />
      {message}
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div className="empty">
      <LoaderCircle className="spin" size={24} />
      <p>Loading live data…</p>
    </div>
  );
}
export function Empty({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Activity size={24} />
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="heading-actions">{actions}</div>
    </div>
  );
}
export function Pagination({
  page,
  totalPages,
  total,
  onChange,
}: {
  page: number;
  totalPages: number;
  total: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="pagination">
      <span>
        {total} result{total !== 1 ? "s" : ""}
      </span>
      <div>
        <button
          className="icon-button"
          aria-label="Previous page"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          <ArrowLeft size={16} />
        </button>
        <span>
          {page + 1} / {Math.max(1, totalPages)}
        </span>
        <button
          className="icon-button"
          aria-label="Next page"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
    >
      <div className="modal-heading">
        <h2>{title}</h2>
        <button
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
        >
          <X size={18} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function RunModal({
  definitionId,
  name,
  onClose,
  onStarted,
}: {
  definitionId: string;
  name: string;
  onClose: () => void;
  onStarted: (run: Execution) => void;
}) {
  const [input, setInput] = useState('{\n  "orderId": "ORD-1001"\n}');
  const [mode, setMode] = useState<Mode>("LOCAL");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const settings = useApi<Settings>("/settings");
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const parsed = JSON.parse(input);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== "object")
        throw new Error("Input must be a JSON object");
      const response = await api.post<Execution>(
        `/workflows/${definitionId}/executions`,
        { input: parsed, mode },
      );
      onStarted(response.data);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Start an execution" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="muted">
          Run <strong>{name}</strong> with a new, durable execution.
        </p>
        <label>
          Execution mode
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value as Mode)}
          >
            <option value="LOCAL">Local worker</option>
            <option value="KAFKA" disabled={!settings.data?.kafkaEnabled}>
              Kafka worker{settings.data?.kafkaEnabled ? "" : " · disabled"}
            </option>
          </select>
        </label>
        <label>
          Workflow input <span className="muted">JSON object</span>
          <textarea
            className="code"
            rows={7}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            spellCheck={false}
          />
        </label>
        <ErrorBox message={error} />
        <div className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="button primary" disabled={busy}>
            <Play size={15} />
            {busy ? "Starting…" : "Start execution"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
export function JsonBlock({ data }: { data: unknown }) {
  return <pre className="json-block">{JSON.stringify(data, null, 2)}</pre>;
}
