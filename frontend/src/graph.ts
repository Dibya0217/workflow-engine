import type { TaskDefinition, WorkflowSpec } from "./types";
export const defaultRetry = {
  maxAttempts: 3,
  initialDelayMs: 1000,
  backoffMultiplier: 2,
  maxDelayMs: 30000,
};
export function newTask(index: number, type = "VALIDATION"): TaskDefinition {
  return {
    name: `task-${index}`,
    type,
    next: null,
    retryPolicy: { ...defaultRetry },
    config: {},
  };
}
export function validateGraph(spec: WorkflowSpec): string | null {
  if (!/^[a-zA-Z0-9_-]{1,100}$/.test(spec.name))
    return "Use letters, numbers, hyphens, or underscores for the workflow name.";
  if (!Number.isInteger(spec.version) || spec.version < 1)
    return "Version must be a positive integer.";
  if (!spec.tasks.length) return "Add at least one task.";
  const map = new Map(spec.tasks.map((t) => [t.name, t]));
  if (map.size !== spec.tasks.length) return "Task names must be unique.";
  for (const task of spec.tasks) {
    if (!/^[a-zA-Z0-9_-]{1,80}$/.test(task.name))
      return "Task names must use letters, numbers, hyphens, or underscores.";
    const p = task.retryPolicy;
    if (
      !Number.isInteger(p.maxAttempts) ||
      p.maxAttempts < 1 ||
      p.maxAttempts > 100 ||
      p.initialDelayMs < 0 ||
      p.maxDelayMs < p.initialDelayMs ||
      p.maxDelayMs < 1 ||
      p.maxDelayMs > 604800000 ||
      p.backoffMultiplier < 1 ||
      p.backoffMultiplier > 100
    )
      return `Check the retry policy for ${task.name}.`;
  }
  const visited = new Set<string>();
  let name: string | null = spec.tasks[0].name;
  while (name) {
    if (visited.has(name))
      return "A sequential workflow cannot contain a cycle.";
    const task: TaskDefinition | undefined = map.get(name);
    if (!task) return `Connection points to missing task: ${name}`;
    visited.add(name);
    name = task.next;
  }
  return visited.size === spec.tasks.length
    ? null
    : "Connect every task into one chain from the first task.";
}
