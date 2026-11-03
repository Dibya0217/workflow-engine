package io.flowlab.task;

import org.springframework.stereotype.Component;

@Component
public class PaymentTaskHandler implements TaskHandler {
  private final SimulatedBusinessService service;

  public PaymentTaskHandler(SimulatedBusinessService service) {
    this.service = service;
  }

  public String getTaskType() {
    return "PAYMENT";
  }

  public TaskResult execute(TaskContext context) {
    return service.execute(getTaskType(), context);
  }
}
