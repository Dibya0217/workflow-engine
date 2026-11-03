package io.flowlab.definition;

import io.flowlab.domain.*;
import java.util.*;

public final class DefinitionValidator {
  private DefinitionValidator() {}

  public static void validate(WorkflowSpec spec, Set<String> taskTypes) {
    if (spec.tasks() == null || spec.tasks().isEmpty())
      throw new IllegalArgumentException("At least one task is required");
    Map<String, TaskDefinition> tasks = new HashMap<>();
    for (TaskDefinition task : spec.tasks()) {
      if (task.name() == null || tasks.putIfAbsent(task.name(), task) != null)
        throw new IllegalArgumentException("Task names must be unique");
      if (!taskTypes.contains(task.type()))
        throw new IllegalArgumentException("Unknown task type: " + task.type());
      RetryPolicy p = task.retryPolicy();
      if (p.maxAttempts() < 1
          || p.maxAttempts() > 100
          || p.initialDelayMs() < 0
          || !Double.isFinite(p.backoffMultiplier())
          || p.backoffMultiplier() < 1
          || p.backoffMultiplier() > 100
          || p.maxDelayMs() < 1
          || p.maxDelayMs() > 604800000
          || p.initialDelayMs() > p.maxDelayMs())
        throw new IllegalArgumentException("Invalid retry policy for " + task.name());
    }
    Set<String> seen = new HashSet<>();
    String current = spec.tasks().getFirst().name();
    while (current != null) {
      if (!tasks.containsKey(current))
        throw new IllegalArgumentException("Unknown next task: " + current);
      if (!seen.add(current)) throw new IllegalArgumentException("Workflow contains a cycle");
      current = tasks.get(current).next();
    }
    if (seen.size() != tasks.size())
      throw new IllegalArgumentException("Every task must belong to one sequential chain");
  }
}
