import { describe, it, expect } from "vitest";
import { newTask, validateGraph } from "./graph";
import type { WorkflowSpec } from "./types";
const make = (): WorkflowSpec => ({
  name: "orders",
  version: 1,
  description: "",
  tasks: [{ ...newTask(1), next: "task-2" }, newTask(2)],
});
describe("sequential graph validation", () => {
  it("accepts a connected chain", () =>
    expect(validateGraph(make())).toBeNull());
  it("rejects a cycle", () => {
    const s = make();
    s.tasks[1].next = "task-1";
    expect(validateGraph(s)).toContain("cycle");
  });
  it("rejects disconnected tasks", () => {
    const s = make();
    s.tasks[0].next = null;
    expect(validateGraph(s)).toContain("chain");
  });
  it("rejects duplicate names", () => {
    const s = make();
    s.tasks[1].name = "task-1";
    expect(validateGraph(s)).toContain("unique");
  });
  it("rejects invalid retry budgets", () => {
    const s = make();
    s.tasks[0].retryPolicy.maxAttempts = 0;
    expect(validateGraph(s)).toContain("retry");
  });
  it("rejects a missing transition target", () => {
    const s = make();
    s.tasks[0].next = "unknown";
    expect(validateGraph(s)).toContain("missing");
  });
});
