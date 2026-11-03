package io.flowlab.task;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ValidationTaskHandler implements TaskHandler {
  public String getTaskType() {
    return "VALIDATION";
  }

  public TaskResult execute(TaskContext context) {
    Object fields = context.config().get("requiredFields");
    if (fields != null && !(fields instanceof List<?>))
      throw new TaskFailure("requiredFields configuration must be an array", false);
    if (fields instanceof List<?> required) {
      for (Object field : required)
        if (!context.input().containsKey(field.toString())
            || context.input().get(field.toString()) == null)
          throw new TaskFailure("Missing required input: " + field, false);
    }
    return new TaskResult(Map.of("validated", true));
  }
}
