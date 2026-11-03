package io.flowlab.domain;

import java.util.Set;

public enum WorkflowStatus {
  CREATED,
  RUNNING,
  PAUSED,
  COMPLETED,
  FAILED,
  CANCELLING,
  CANCELLED;

  public boolean terminal() {
    return this == COMPLETED || this == CANCELLED;
  }

  public void requireTransition(WorkflowStatus next) {
    boolean valid =
        switch (this) {
          case CREATED -> Set.of(RUNNING, CANCELLING).contains(next);
          case RUNNING -> Set.of(PAUSED, COMPLETED, FAILED, CANCELLING).contains(next);
          case PAUSED -> Set.of(RUNNING, COMPLETED, FAILED, CANCELLING).contains(next);
          case FAILED -> Set.of(RUNNING, CANCELLING).contains(next);
          case CANCELLING -> next == CANCELLED;
          case COMPLETED, CANCELLED -> false;
        };
    if (!valid)
      throw new IllegalStateException("Cannot transition workflow from " + this + " to " + next);
  }
}
