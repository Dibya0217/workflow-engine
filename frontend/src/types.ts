export type Json = Record<string, unknown>;
export type Status =
  | "CREATED"
  | "RUNNING"
  | "PAUSED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLING"
  | "CANCELLED"
  | "PENDING"
  | "RETRY_WAIT"
  | "SKIPPED";
export type Mode = "LOCAL" | "KAFKA";
export interface RetryPolicy {
  maxAttempts: number;
  initialDelayMs: number;
  backoffMultiplier: number;
  maxDelayMs: number;
}
export interface TaskDefinition {
  name: string;
  type: string;
  next: string | null;
  retryPolicy: RetryPolicy;
  config: Json;
}
export interface WorkflowSpec {
  name: string;
  version: number;
  description: string;
  tasks: TaskDefinition[];
}
export interface Definition {
  id: string;
  name: string;
  version: number;
  description: string;
  active: boolean;
  specification: WorkflowSpec;
  createdAt: string;
}
export interface Execution {
  id: string;
  workflowDefinitionId: string;
  workflowVersion: number;
  status: Status;
  mode: Mode;
  currentTask: string | null;
  inputData: Json;
  outputData: Json;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
}
export interface Task {
  id: string;
  workflowExecutionId: string;
  taskName: string;
  taskType: string;
  position: number;
  status: Status;
  attemptCount: number;
  inputData: Json;
  outputData: Json;
  errorMessage: string | null;
  retryDueAt: string | null;
  leaseUntil: string | null;
  startedAt: string | null;
  completedAt: string | null;
}
export interface Event {
  id: number;
  taskExecutionId: string | null;
  eventType: string;
  details: Json;
  createdAt: string;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Stats {
  totalWorkflows: number;
  activeExecutions: number;
  completedExecutions: number;
  failedExecutions: number;
  totalExecutions: number;
  averageDurationMs: number;
}
export interface Schedule {
  id: string;
  workflowDefinitionId: string;
  name: string;
  intervalSeconds: number;
  enabled: boolean;
  mode: Mode;
  inputData: Json;
  nextRunAt: string;
}
export interface Settings {
  kafkaEnabled: boolean;
  leaseSeconds: number;
  workerConcurrency: number;
  coordination: string;
  deliverySemantics: string;
  version: string;
}
export interface Session {
  accessToken: string;
  username: string;
  role: "ADMIN" | "USER";
  expiresIn: number;
}
