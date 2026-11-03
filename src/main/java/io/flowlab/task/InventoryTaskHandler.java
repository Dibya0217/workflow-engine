package io.flowlab.task;

import org.springframework.stereotype.Component;

@Component
public class InventoryTaskHandler implements TaskHandler {
  private final SimulatedBusinessService service;

  public InventoryTaskHandler(SimulatedBusinessService service) {
    this.service = service;
  }

  public String getTaskType() {
    return "INVENTORY";
  }

  public TaskResult execute(TaskContext context) {
    return service.execute(getTaskType(), context);
  }
}
