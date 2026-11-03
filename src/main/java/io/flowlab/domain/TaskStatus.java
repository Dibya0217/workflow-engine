package io.flowlab.domain;

import java.util.Set;

public enum TaskStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  RETRY_WAIT,
  SKIPPED,
  CANCELLED;

  public void requireTransition(TaskStatus next) {
    boolean valid =
        switch (this) {
          case PENDING -> Set.of(RUNNING, SKIPPED, CANCELLED).contains(next);
          case RUNNING -> Set.of(COMPLETED, FAILED, RETRY_WAIT, CANCELLED).contains(next);
          case RETRY_WAIT -> Set.of(RUNNING, FAILED, CANCELLED).contains(next);
          case FAILED -> Set.of(RETRY_WAIT, CANCELLED).contains(next);
          case COMPLETED, SKIPPED, CANCELLED -> false;
        };
    if (!valid)
      throw new IllegalStateException("Cannot transition task from " + this + " to " + next);
  }
}
