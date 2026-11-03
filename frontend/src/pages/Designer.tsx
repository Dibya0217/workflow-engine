import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  Position,
  ReactFlow,
  useNodesState,
} from "@xyflow/react";
import type { Connection, Node, NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { ArrowLeft, Check, GitBranch, Plus, Save, Trash2 } from "lucide-react";
import { api, errorMessage } from "../api";
import { useAuth } from "../auth";
import type { Definition, TaskDefinition, WorkflowSpec } from "../types";
import { ErrorBox, Loading, PageHeader } from "../components/ui";
import { defaultRetry, newTask, validateGraph } from "../graph";
type TaskNode = Node<{ label: string; taskType: string; entry: boolean }>;
function TaskNodeView({ data, selected }: NodeProps<TaskNode>) {
  return (
    <div className={`flow-task ${selected ? "selected" : ""}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-task-icon">
        <GitBranch size={18} />
      </div>
      <div>
        <small>{data.entry ? "ENTRY TASK" : data.taskType}</small>
        <strong>{data.label}</strong>
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
const nodeTypes = { task: TaskNodeView };
export default function Designer() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { session } = useAuth();
  const editable = session?.role === "ADMIN";
  const [spec, setSpec] = useState<WorkflowSpec>({
    name: "",
    version: 1,
    description: "",
    tasks: [newTask(1)],
  });
  const [selected, setSelected] = useState("task-1");
  const [nodes, setNodes, onNodesChange] = useNodesState<TaskNode>([]);
  const [types, setTypes] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(!!id);
  const [busy, setBusy] = useState(false);
  const [configText, setConfigText] = useState("{}");
  const [configError, setConfigError] = useState("");
  const [originalVersion, setOriginalVersion] = useState<number>();
  useEffect(() => {
    let live = true;
    void api
      .get<string[]>("/task-types")
      .then((r) => {
        if (live) setTypes(r.data);
      })
      .catch((e) => {
        if (live) setError(errorMessage(e));
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    if (!id) return;
    let live = true;
    setLoading(true);
    void api
      .get<Definition>(`/workflows/${id}`)
      .then((r) => {
        if (live) {
          setSpec({
            ...r.data.specification,
            version: r.data.version + 1,
            tasks: r.data.specification.tasks.map((t) => ({
              ...t,
              retryPolicy: t.retryPolicy ?? { ...defaultRetry },
              config: t.config ?? {},
            })),
          });
          setOriginalVersion(r.data.version);
          setSelected(r.data.specification.tasks[0].name);
        }
      })
      .catch((e) => {
        if (live) setError(errorMessage(e));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [id]);
  useEffect(() => {
    setNodes((previous) =>
      spec.tasks.map((t, i) => ({
        id: t.name,
        type: "task",
        position: previous.find((n) => n.id === t.name)?.position ?? {
          x: 60 + i * 290,
          y: 150,
        },
        selected: t.name === selected,
        data: { label: t.name, taskType: t.type, entry: i === 0 },
      })),
    );
  }, [spec.tasks, selected, setNodes]);
  const task = spec.tasks.find((t) => t.name === selected);
  useEffect(() => {
    setConfigText(JSON.stringify(task?.config ?? {}, null, 2));
    setConfigError("");
  }, [selected, loading]); // Preserve partial JSON while typing.
  const edges = spec.tasks
    .filter((t) => t.next)
    .map((t) => ({
      id: `${t.name}-${t.next}`,
      source: t.name,
      target: t.next!,
      animated: false,
      style: { stroke: "#718b84", strokeWidth: 1.5 },
    }));
  function updateTask(patch: Partial<TaskDefinition>) {
    setNotice("");
    setSpec((current) => ({
      ...current,
      tasks: current.tasks.map((t) =>
        t.name === selected ? { ...t, ...patch } : t,
      ),
    }));
  }
  const connect = useCallback(
    (connection: Connection) => {
      if (!editable) return;
      setSpec((s) => ({
        ...s,
        tasks: s.tasks.map((t) =>
          t.name === connection.source ? { ...t, next: connection.target } : t,
        ),
      }));
      setNotice("");
    },
    [editable],
  );
  function add() {
    let i = spec.tasks.length + 1;
    while (spec.tasks.some((t) => t.name === `task-${i}`)) i++;
    const task = newTask(i, types[0] ?? "VALIDATION");
    setSpec((s) => ({
      ...s,
      tasks: [
        ...s.tasks.map((t, index) =>
          index === s.tasks.length - 1 && t.next === null
            ? { ...t, next: task.name }
            : t,
        ),
        task,
      ],
    }));
    setSelected(task.name);
    setNotice("");
  }
  function remove() {
    if (!task) return;
    setSpec((s) => ({
      ...s,
      tasks: s.tasks
        .filter((t) => t.name !== selected)
        .map((t) => (t.next === selected ? { ...t, next: task.next } : t)),
    }));
    setSelected(spec.tasks.find((t) => t.name !== selected)?.name ?? "");
  }
  async function validate(save = false) {
    setError("");
    setNotice("");
    const invalid = validateGraph(spec) || configError;
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    try {
      await api.post("/workflows/validate", spec);
      if (save) {
        const response = id
          ? await api.put<Definition>(`/workflows/${id}`, spec)
          : await api.post<Definition>("/workflows", spec);
        navigate("/workflows", { state: { saved: response.data.id } });
      } else setNotice("Valid sequential workflow. Every task is reachable.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  if (loading) return <Loading />;
  return (
    <>
      <Link className="back-link" to="/workflows">
        <ArrowLeft size={14} />
        Workflow library
      </Link>
      <PageHeader
        eyebrow="WORKFLOW DESIGNER"
        title={id ? spec.name || "Workflow" : "Turn a process into a flow."}
        description={
          id
            ? `Viewing v${originalVersion}. Saving creates immutable version ${spec.version}.`
            : "Connect tasks, set retry policies, and make it repeatable."
        }
        actions={
          editable && (
            <>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => void validate()}
              >
                <Check size={16} />
                Validate
              </button>
              <button
                className="button primary"
                disabled={busy}
                onClick={() => void validate(true)}
              >
                <Save size={16} />
                {busy
                  ? "Saving…"
                  : id
                    ? `Save version ${spec.version}`
                    : "Save workflow"}
              </button>
            </>
          )
        }
      />
      <ErrorBox message={error} />
      {notice && (
        <div className="success" role="status">
          <Check size={16} />
          {notice}
        </div>
      )}
      <div className="definition-fields">
        <label>
          Workflow name
          <input
            value={spec.name}
            disabled={!editable || !!id}
            placeholder="order-processing"
            onChange={(e) => setSpec((s) => ({ ...s, name: e.target.value }))}
          />
        </label>
        <label className="description-field">
          Description
          <input
            value={spec.description}
            disabled={!editable}
            placeholder="What does this workflow do?"
            onChange={(e) =>
              setSpec((s) => ({ ...s, description: e.target.value }))
            }
          />
        </label>
        <label>
          Version
          <input
            type="number"
            min={originalVersion ? originalVersion + 1 : 1}
            value={spec.version}
            disabled={!editable}
            onChange={(e) =>
              setSpec((s) => ({ ...s, version: Number(e.target.value) }))
            }
          />
        </label>
      </div>
      <div className="designer-layout">
        <section className="designer-canvas">
          <div className="canvas-toolbar">
            <span>
              <GitBranch size={16} />
              Sequential workflow <small>· {spec.tasks.length} tasks</small>
            </span>
            {editable && (
              <button className="button secondary small" onClick={add}>
                <Plus size={15} />
                Add task
              </button>
            )}
          </div>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onConnect={connect}
            onNodeClick={(_, node) => setSelected(node.id)}
            onEdgesDelete={(deleted) => {
              if (editable)
                setSpec((s) => ({
                  ...s,
                  tasks: s.tasks.map((t) =>
                    deleted.some((e) => e.source === t.name)
                      ? { ...t, next: null }
                      : t,
                  ),
                }));
            }}
            nodesConnectable={editable}
            edgesReconnectable={editable}
            deleteKeyCode={null}
            nodesDraggable
            fitView
            minZoom={0.3}
            maxZoom={1.5}
            colorMode="dark"
          >
            <Background
              variant={BackgroundVariant.Dots}
              gap={20}
              size={1}
              color="#2b3541"
            />
            <Controls showInteractive={false} />
          </ReactFlow>
          <div className="canvas-hint">
            Drag to arrange · Connect handles to set the next step · Select a
            task to configure
          </div>
        </section>
        <aside className="task-properties">
          <div className="panel-header">
            <h2>Task properties</h2>
            {editable && task && (
              <button
                className="icon-button danger-text"
                aria-label="Remove selected task"
                onClick={remove}
              >
                <Trash2 size={16} />
              </button>
            )}
          </div>
          {task ? (
            <div className="properties-body">
              <fieldset disabled={!editable}>
                <label>
                  Task name
                  <input
                    key={selected}
                    defaultValue={task.name}
                    onBlur={(e) => {
                      const value = e.target.value.trim();
                      if (!value || value === selected) return;
                      if (spec.tasks.some((t) => t.name === value)) {
                        setError("Task name already exists.");
                        return;
                      }
                      setSpec((s) => ({
                        ...s,
                        tasks: s.tasks.map((t) => ({
                          ...t,
                          name: t.name === selected ? value : t.name,
                          next: t.next === selected ? value : t.next,
                        })),
                      }));
                      setSelected(value);
                    }}
                  />
                </label>
                <label>
                  Handler type
                  <select
                    value={task.type}
                    onChange={(e) => updateTask({ type: e.target.value })}
                  >
                    {types.map((type) => (
                      <option key={type}>{type}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Next task
                  <select
                    value={task.next ?? ""}
                    onChange={(e) =>
                      updateTask({ next: e.target.value || null })
                    }
                  >
                    <option value="">End workflow</option>
                    {spec.tasks
                      .filter((t) => t.name !== task.name)
                      .map((t) => (
                        <option value={t.name} key={t.name}>
                          {t.name}
                        </option>
                      ))}
                  </select>
                </label>
                <div className="property-section">
                  <h3>Retry policy</h3>
                  <p>Attempts include the initial execution.</p>
                </div>
                <div className="two-columns compact">
                  <label>
                    Max attempts
                    <input
                      type="number"
                      min="1"
                      max="100"
                      value={task.retryPolicy.maxAttempts}
                      onChange={(e) =>
                        updateTask({
                          retryPolicy: {
                            ...task.retryPolicy,
                            maxAttempts: Number(e.target.value),
                          },
                        })
                      }
                    />
                  </label>
                  <label>
                    Backoff multiplier
                    <input
                      type="number"
                      min="1"
                      max="100"
                      step="0.5"
                      value={task.retryPolicy.backoffMultiplier}
                      onChange={(e) =>
                        updateTask({
                          retryPolicy: {
                            ...task.retryPolicy,
                            backoffMultiplier: Number(e.target.value),
                          },
                        })
                      }
                    />
                  </label>
                </div>
                <label>
                  Initial delay (ms)
                  <input
                    type="number"
                    min="0"
                    value={task.retryPolicy.initialDelayMs}
                    onChange={(e) =>
                      updateTask({
                        retryPolicy: {
                          ...task.retryPolicy,
                          initialDelayMs: Number(e.target.value),
                        },
                      })
                    }
                  />
                </label>
                <label>
                  Maximum delay (ms)
                  <input
                    type="number"
                    min="1"
                    value={task.retryPolicy.maxDelayMs}
                    onChange={(e) =>
                      updateTask({
                        retryPolicy: {
                          ...task.retryPolicy,
                          maxDelayMs: Number(e.target.value),
                        },
                      })
                    }
                  />
                </label>
                <label>
                  Handler configuration
                  <textarea
                    className="code"
                    rows={5}
                    spellCheck={false}
                    value={configText}
                    onChange={(e) => {
                      setConfigText(e.target.value);
                      try {
                        const config = JSON.parse(e.target.value);
                        if (
                          !config ||
                          Array.isArray(config) ||
                          typeof config !== "object"
                        )
                          throw new Error();
                        updateTask({ config });
                        setConfigError("");
                      } catch {
                        setConfigError(
                          "Handler configuration must be a valid JSON object.",
                        );
                      }
                    }}
                  />
                </label>
                <ErrorBox message={configError} />
                <p className="field-help">
                  Validation: requiredFields. Simulated services: failAttempts
                  or permanentFailure.
                </p>
              </fieldset>
            </div>
          ) : (
            <div className="empty">
              <p>Add or select a task to configure it.</p>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}
