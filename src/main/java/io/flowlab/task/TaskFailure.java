package io.flowlab.task;

public class TaskFailure extends RuntimeException {
  private final boolean retryable;

  public TaskFailure(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }
}
