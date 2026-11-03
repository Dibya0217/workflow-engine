package io.flowlab.task;

public interface TaskHandler {
  String getTaskType();

  TaskResult execute(TaskContext context);
}
