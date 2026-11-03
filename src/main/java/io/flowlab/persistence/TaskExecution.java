package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_executions")
public class TaskExecution {
  @Id public UUID id = UUID.randomUUID();
  @Version public long rowVersion;

  @Column(nullable = false, updatable = false)
  public UUID workflowExecutionId;

  public String taskName;
  public String taskType;
  public int position;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public TaskStatus status;

  public int attemptCount;
  public int retryBase;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> inputData = Map.of();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> outputData = Map.of();

  public String errorMessage;
  public Instant retryDueAt;
  public UUID leaseToken;
  public Instant leaseUntil;
  public Instant startedAt;
  public Instant completedAt;
  public Instant createdAt;
  public Instant updatedAt;

  public void transition(TaskStatus next, Instant now) {
    status.requireTransition(next);
    status = next;
    updatedAt = now;
  }
}
