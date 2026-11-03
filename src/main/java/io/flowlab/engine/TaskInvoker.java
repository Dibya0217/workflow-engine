package io.flowlab.engine;

import io.flowlab.task.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class TaskInvoker {
  private static final Logger log = LoggerFactory.getLogger(TaskInvoker.class);
  private final TaskHandlerRegistry registry;

  public TaskInvoker(TaskHandlerRegistry registry) {
    this.registry = registry;
  }

  public TaskOutcome invoke(Dispatch dispatch) {
    try (var ignored = MDC.putCloseable("correlationId", dispatch.context().correlationId())) {
      try {
        return TaskOutcome.success(
            dispatch,
            registry.get(dispatch.context().taskType()).execute(dispatch.context()).output());
      } catch (TaskFailure e) {
        log.warn(
            "Task {} attempt {} failed: {}",
            dispatch.context().taskId(),
            dispatch.context().attempt(),
            e.getMessage());
        return TaskOutcome.failure(dispatch, e.getMessage(), e.retryable());
      } catch (Exception e) {
        log.error("Unexpected task failure {}", dispatch.context().taskId(), e);
        return TaskOutcome.failure(
            dispatch, "Unexpected handler failure: " + e.getClass().getSimpleName(), true);
      }
    }
  }
}
