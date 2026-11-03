package io.flowlab.engine;

import io.flowlab.domain.*;
import io.flowlab.persistence.*;
import io.flowlab.retry.RetryManager;
import io.flowlab.task.TaskContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All state changes serialize on the execution row; handlers never run under that lock. */
@Service
public class EngineTransactions {
  private final WorkflowExecutionRepository executions;
  private final WorkflowDefinitionRepository definitions;
  private final TaskExecutionRepository tasks;
  private final DatabaseSupport db;
  private final RetryManager retries;
  private final MeterRegistry metrics;
  private final long leaseSeconds;
  private final boolean kafkaEnabled;

  public EngineTransactions(
      WorkflowExecutionRepository executions,
      WorkflowDefinitionRepository definitions,
      TaskExecutionRepository tasks,
      DatabaseSupport db,
      RetryManager retries,
      MeterRegistry metrics,
      @Value("${flowlab.lease-seconds}") long leaseSeconds,
      @Value("${flowlab.kafka-enabled}") boolean kafkaEnabled) {
    if (leaseSeconds < 1) throw new IllegalArgumentException("Lease must be positive");
    this.executions = executions;
    this.definitions = definitions;
    this.tasks = tasks;
    this.db = db;
    this.retries = retries;
    this.metrics = metrics;
    this.leaseSeconds = leaseSeconds;
    this.kafkaEnabled = kafkaEnabled;
  }

  public record Claim(ExecutionMode mode, Dispatch dispatch) {}

  @Transactional
  public Optional<Claim> claim() {
    var candidate = executions.claimable(kafkaEnabled);
    if (candidate.isEmpty()) return Optional.empty();
    var run = candidate.get();
    var spec = definitions.findById(run.workflowDefinitionId).orElseThrow().specification;
    var task = tasks.findByWorkflowExecutionIdAndTaskName(run.id, run.currentTask).orElseThrow();
    Instant now = db.now();
    if (run.status == WorkflowStatus.CREATED) {
      run.transition(WorkflowStatus.RUNNING, now);
      run.startedAt = now;
    }
    task.transition(TaskStatus.RUNNING, now);
    task.attemptCount++;
    task.startedAt = now;
    task.retryDueAt = null;
    task.leaseToken = UUID.randomUUID();
    task.leaseUntil = now.plusSeconds(leaseSeconds);
    task.inputData = new HashMap<>(run.outputData);
    run.updatedAt = now;
    var context =
        new TaskContext(
            run.id,
            task.id,
            task.taskName,
            task.taskType,
            task.attemptCount,
            run.id + ":" + task.taskName,
            run.correlationId,
            Collections.unmodifiableMap(new HashMap<>(task.inputData)),
            spec.task(task.taskName).config());
    var dispatch = new Dispatch(1, UUID.randomUUID(), task.leaseToken, context);
    db.event(
        run.id,
        task.id,
        "TASK_STARTED",
        Map.of("attempt", task.attemptCount, "leaseUntil", task.leaseUntil));
    if (run.mode == ExecutionMode.KAFKA)
      db.outbox("flowlab.tasks.commands", run.id.toString(), "TASK_COMMAND", dispatch);
    return Optional.of(new Claim(run.mode, dispatch));
  }

  @Transactional
  public boolean complete(TaskOutcome result) {
    if (result.eventVersion() != 1) throw new IllegalArgumentException("Unsupported event version");
    var found = executions.lock(result.executionId());
    if (found.isEmpty()) return false;
    var run = found.get();
    var task = tasks.findById(result.taskId()).orElse(null);
    Instant now = db.now();
    if (task == null
        || !task.workflowExecutionId.equals(run.id)
        || task.status != TaskStatus.RUNNING
        || !Objects.equals(task.leaseToken, result.leaseToken())
        || !task.leaseUntil.isAfter(now)
        || !EnumSet.of(WorkflowStatus.RUNNING, WorkflowStatus.PAUSED).contains(run.status)) {
      metrics.counter("flowlab.results.stale").increment();
      return false;
    }
    var spec =
        definitions
            .findById(run.workflowDefinitionId)
            .orElseThrow()
            .specification
            .task(task.taskName);
    metrics
        .timer("flowlab.task.duration", "type", task.taskType)
        .record(Duration.between(task.startedAt, now));
    if (!result.successful()) {
      retries.fail(run, task, spec, result.error(), result.retryable(), now);
      return true;
    }
    task.transition(TaskStatus.COMPLETED, now);
    task.outputData = new HashMap<>(result.output());
    task.completedAt = now;
    task.errorMessage = null;
    task.leaseToken = null;
    task.leaseUntil = null;
    var output = new HashMap<>(run.outputData);
    output.putAll(result.output());
    run.outputData = output;
    run.currentTask = spec.next();
    run.updatedAt = now;
    db.event(run.id, task.id, "TASK_COMPLETED", Map.of("attempt", task.attemptCount));
    if (spec.next() == null) {
      run.transition(WorkflowStatus.COMPLETED, now);
      run.completedAt = now;
      db.event(run.id, null, "WORKFLOW_COMPLETED", Map.of());
      metrics.counter("flowlab.executions.completed").increment();
    }
    return true;
  }

  @Transactional
  public boolean recoverOne() {
    var expired = executions.expired();
    if (expired.isEmpty()) return false;
    var run = expired.get();
    Instant now = db.now();
    var task = tasks.findByWorkflowExecutionIdAndTaskName(run.id, run.currentTask).orElseThrow();
    var spec =
        definitions
            .findById(run.workflowDefinitionId)
            .orElseThrow()
            .specification
            .task(task.taskName);
    db.event(run.id, task.id, "TASK_LEASE_EXPIRED", Map.of("attempt", task.attemptCount));
    retries.fail(
        run,
        task,
        spec,
        "Execution lease expired; the previous worker's outcome is unknown",
        true,
        now);
    metrics.counter("flowlab.recovery.attempts").increment();
    return true;
  }

  @Transactional(readOnly = true)
  public boolean isCurrent(Dispatch d) {
    var task = tasks.findById(d.context().taskId()).orElse(null);
    return task != null
        && task.workflowExecutionId.equals(d.context().executionId())
        && task.status == TaskStatus.RUNNING
        && Objects.equals(task.leaseToken, d.leaseToken())
        && task.leaseUntil.isAfter(db.now());
  }
}
