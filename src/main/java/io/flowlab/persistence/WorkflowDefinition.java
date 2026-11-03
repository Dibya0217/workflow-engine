package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {
  @Id public UUID id = UUID.randomUUID();
  @Version public long rowVersion;

  @Column(nullable = false, updatable = false)
  public String name;

  @Column(nullable = false, updatable = false)
  public int version;

  @Column(updatable = false)
  public String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  public WorkflowSpec specification;

  public boolean active;

  @Column(nullable = false, updatable = false)
  public Instant createdAt;
}
