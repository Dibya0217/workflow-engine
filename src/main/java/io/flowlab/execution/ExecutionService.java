package io.flowlab.execution;

import io.flowlab.domain.*;
import io.flowlab.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExecutionService {
  private final WorkflowDefinitionRepository definitions;
  private final WorkflowExecutionRepository executions;
  private final TaskExecutionRepository tasks;
  private final DatabaseSupport db;
  private final boolean kafkaEnabled;

  public ExecutionService(
      WorkflowDefinitionRepository definitions,
      WorkflowExecutionRepository executions,
      TaskExecutionRepository tasks,
      DatabaseSupport db,
      @Value("${flowlab.kafka-enabled}") boolean kafkaEnabled) {
    this.definitions = definitions;
    this.executions = executions;
    this.tasks = tasks;
    this.db = db;
    this.kafkaEnabled = kafkaEnabled;
  }

  public WorkflowExecution get(UUID id) {
    return executions
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found"));
  }

  public WorkflowExecution lock(UUID id) {
    return executions
        .lock(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found"));
  }

  @Transactional
  public WorkflowExecution start(
      UUID definitionId, Map<String, Object> input, ExecutionMode mode, String correlationId) {
    if (mode == ExecutionMode.KAFKA && !kafkaEnabled)
      throw new IllegalArgumentException("Kafka execution is disabled");
    var definition =
        definitions
            .lock(definitionId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    if (!definition.active) throw new IllegalStateException("Workflow is inactive");
    Instant now = db.now();
    var run = new WorkflowExecution();
    run.workflowDefinitionId = definitionId;
    run.workflowVersion = definition.version;
    run.status = WorkflowStatus.CREATED;
    run.mode = mode;
    run.inputData = new HashMap<>(input);
    run.outputData = new HashMap<>(input);
    run.currentTask = definition.specification.tasks().getFirst().name();
    run.createdAt = now;
    run.updatedAt = now;
    run.correlationId = correlationId == null ? UUID.randomUUID().toString() : correlationId;
    executions.saveAndFlush(run);
    String name = run.currentTask;
    int position = 0;
    while (name != null) {
      TaskDefinition spec = definition.specification.task(name);
      var task = new TaskExecution();
      task.workflowExecutionId = run.id;
      task.taskName = name;
      task.taskType = spec.type();
      task.position = position++;
      task.status = TaskStatus.PENDING;
      task.createdAt = now;
      task.updatedAt = now;
      tasks.save(task);
      name = spec.next();
    }
    tasks.flush();
    db.event(run.id, null, "WORKFLOW_CREATED", Map.of("mode", mode));
    return run;
  }

  @Transactional
  public WorkflowExecution control(UUID id, String action) {
    var run = lock(id);
    Instant now = db.now();
    switch (action) {
      case "pause" -> run.transition(WorkflowStatus.PAUSED, now);
      case "resume" -> {
        if (run.status != WorkflowStatus.PAUSED)
          throw new IllegalStateException("Only paused executions can resume");
        run.transition(WorkflowStatus.RUNNING, now);
      }
      case "retry" -> {
        if (run.status != WorkflowStatus.FAILED)
          throw new IllegalStateException("Only failed executions can retry");
        var task = tasks.findByWorkflowExecutionIdAndTaskName(id, run.currentTask).orElseThrow();
        task.transition(TaskStatus.RETRY_WAIT, now);
        task.retryDueAt = now;
        task.retryBase = task.attemptCount;
        task.completedAt = null;
        task.errorMessage = null;
        run.transition(WorkflowStatus.RUNNING, now);
        run.completedAt = null;
      }
      case "cancel" -> {
        run.transition(WorkflowStatus.CANCELLING, now);
        for (var task : tasks.findByWorkflowExecutionIdOrderByPosition(id)) {
          if (EnumSet.of(
                  TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.RETRY_WAIT, TaskStatus.FAILED)
              .contains(task.status)) {
            task.transition(TaskStatus.CANCELLED, now);
            task.leaseToken = null;
            task.leaseUntil = null;
            task.completedAt = now;
          }
        }
        run.transition(WorkflowStatus.CANCELLED, now);
        run.completedAt = now;
      }
      default -> throw new IllegalArgumentException("Unknown execution action");
    }
    db.event(
        run.id, null, "WORKFLOW_" + action.toUpperCase(Locale.ROOT), Map.of("status", run.status));
    return run;
  }
}
