package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecution {
  @Id public UUID id = UUID.randomUUID();
  @Version public long rowVersion;

  @Column(nullable = false, updatable = false)
  public UUID workflowDefinitionId;

  public int workflowVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public WorkflowStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ExecutionMode mode;

  public String currentTask;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> inputData = Map.of();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> outputData = Map.of();

  public String correlationId;
  public Instant createdAt;
  public Instant updatedAt;
  public Instant startedAt;
  public Instant completedAt;

  public void transition(WorkflowStatus next, Instant now) {
    status.requireTransition(next);
    status = next;
    updatedAt = now;
  }
}
