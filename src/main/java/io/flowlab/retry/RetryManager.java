package io.flowlab.retry;

import io.flowlab.domain.*;
import io.flowlab.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RetryManager {
  private final DatabaseSupport db;
  private final MeterRegistry metrics;

  public RetryManager(DatabaseSupport db, MeterRegistry metrics) {
    this.db = db;
    this.metrics = metrics;
  }

  public void fail(
      WorkflowExecution run,
      TaskExecution task,
      TaskDefinition spec,
      String error,
      boolean retryable,
      Instant now) {
    task.errorMessage =
        error == null ? "Task failed" : error.substring(0, Math.min(error.length(), 4000));
    task.leaseToken = null;
    task.leaseUntil = null;
    int budgetAttempt = task.attemptCount - task.retryBase;
    if (retryable && budgetAttempt < spec.retryPolicy().maxAttempts()) {
      task.transition(TaskStatus.RETRY_WAIT, now);
      task.retryDueAt = now.plusMillis(spec.retryPolicy().delayAfter(budgetAttempt));
      db.event(
          run.id,
          task.id,
          "TASK_RETRY_SCHEDULED",
          Map.of(
              "attempt", task.attemptCount, "dueAt", task.retryDueAt, "error", task.errorMessage));
      metrics.counter("flowlab.task.retries", "type", task.taskType).increment();
    } else {
      task.transition(TaskStatus.FAILED, now);
      task.completedAt = now;
      run.transition(WorkflowStatus.FAILED, now);
      run.completedAt = now;
      db.event(
          run.id,
          task.id,
          "TASK_FAILED",
          Map.of("attempt", task.attemptCount, "error", task.errorMessage));
      metrics.counter("flowlab.task.failures", "type", task.taskType).increment();
      if (run.mode == ExecutionMode.KAFKA)
        db.outbox(
            "flowlab.tasks.dlt",
            run.id.toString(),
            "TASK_PERMANENT_FAILURE",
            Map.of(
                "eventVersion",
                1,
                "executionId",
                run.id,
                "taskId",
                task.id,
                "error",
                task.errorMessage,
                "correlationId",
                run.correlationId));
    }
    run.updatedAt = now;
  }
}
