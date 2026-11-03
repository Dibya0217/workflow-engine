package io.flowlab.api;

import io.flowlab.domain.*;
import io.flowlab.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public final class Views {
  private Views() {}

  public record PageView<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <S, T> PageView<T> of(Page<S> page, Function<S, T> map) {
      return new PageView<>(
          page.getContent().stream().map(map).toList(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }

  public record DefinitionView(
      UUID id,
      String name,
      int version,
      String description,
      boolean active,
      WorkflowSpec specification,
      Instant createdAt) {
    public static DefinitionView of(WorkflowDefinition d) {
      return new DefinitionView(
          d.id, d.name, d.version, d.description, d.active, d.specification, d.createdAt);
    }
  }

  public record ExecutionView(
      UUID id,
      UUID workflowDefinitionId,
      int workflowVersion,
      WorkflowStatus status,
      ExecutionMode mode,
      String currentTask,
      Map<String, Object> inputData,
      Map<String, Object> outputData,
      String correlationId,
      Instant createdAt,
      Instant updatedAt,
      Instant startedAt,
      Instant completedAt) {
    public static ExecutionView of(WorkflowExecution e) {
      return new ExecutionView(
          e.id,
          e.workflowDefinitionId,
          e.workflowVersion,
          e.status,
          e.mode,
          e.currentTask,
          e.inputData,
          e.outputData,
          e.correlationId,
          e.createdAt,
          e.updatedAt,
          e.startedAt,
          e.completedAt);
    }
  }

  public record TaskView(
      UUID id,
      UUID workflowExecutionId,
      String taskName,
      String taskType,
      int position,
      TaskStatus status,
      int attemptCount,
      Map<String, Object> inputData,
      Map<String, Object> outputData,
      String errorMessage,
      Instant retryDueAt,
      Instant leaseUntil,
      Instant startedAt,
      Instant completedAt) {
    public static TaskView of(TaskExecution t) {
      return new TaskView(
          t.id,
          t.workflowExecutionId,
          t.taskName,
          t.taskType,
          t.position,
          t.status,
          t.attemptCount,
          t.inputData,
          t.outputData,
          t.errorMessage,
          t.retryDueAt,
          t.leaseUntil,
          t.startedAt,
          t.completedAt);
    }
  }

  public record ScheduleView(
      UUID id,
      UUID workflowDefinitionId,
      String name,
      long intervalSeconds,
      boolean enabled,
      ExecutionMode mode,
      Map<String, Object> inputData,
      Instant nextRunAt) {
    public static ScheduleView of(WorkflowSchedule s) {
      return new ScheduleView(
          s.id,
          s.workflowDefinitionId,
          s.name,
          s.intervalSeconds,
          s.enabled,
          s.mode,
          s.inputData,
          s.nextRunAt);
    }
  }
}
