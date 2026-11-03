package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_schedules")
public class WorkflowSchedule {
  @Id public UUID id = UUID.randomUUID();
  @Version public long rowVersion;
  public UUID workflowDefinitionId;
  public String name;
  public long intervalSeconds;
  public boolean enabled;

  @Enumerated(EnumType.STRING)
  public ExecutionMode mode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  public Map<String, Object> inputData = Map.of();

  public Instant nextRunAt;
  public Instant createdAt;
  public Instant updatedAt;
}
