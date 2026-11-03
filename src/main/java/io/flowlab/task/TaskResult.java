package io.flowlab.task;

import java.util.Map;

public record TaskResult(Map<String, Object> output) {
  public TaskResult {
    output = Map.copyOf(output);
  }
}
