package io.flowlab.task;

import org.springframework.stereotype.Component;

@Component
public class NotificationTaskHandler implements TaskHandler {
  private final SimulatedBusinessService service;

  public NotificationTaskHandler(SimulatedBusinessService service) {
    this.service = service;
  }

  public String getTaskType() {
    return "NOTIFICATION";
  }

  public TaskResult execute(TaskContext context) {
    return service.execute(getTaskType(), context);
  }
}
