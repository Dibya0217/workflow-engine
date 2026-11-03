package io.flowlab.task;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {
  private final Map<String, TaskHandler> handlers;

  public TaskHandlerRegistry(List<TaskHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(Collectors.toUnmodifiableMap(TaskHandler::getTaskType, Function.identity()));
  }

  public Set<String> types() {
    return handlers.keySet();
  }

  public TaskHandler get(String type) {
    TaskHandler handler = handlers.get(type);
    if (handler == null) throw new TaskFailure("No registered handler for " + type, false);
    return handler;
  }
}
