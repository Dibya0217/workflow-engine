package io.flowlab.engine;

import java.util.*;

public record TaskOutcome(
    int eventVersion,
    UUID eventId,
    UUID executionId,
    UUID taskId,
    UUID leaseToken,
    String correlationId,
    boolean successful,
    boolean retryable,
    Map<String, Object> output,
    String error) {
  public static TaskOutcome success(Dispatch d, Map<String, Object> output) {
    return new TaskOutcome(
        1,
        UUID.randomUUID(),
        d.context().executionId(),
        d.context().taskId(),
        d.leaseToken(),
        d.context().correlationId(),
        true,
        false,
        output,
        null);
  }

  public static TaskOutcome failure(Dispatch d, String error, boolean retryable) {
    return new TaskOutcome(
        1,
        UUID.randomUUID(),
        d.context().executionId(),
        d.context().taskId(),
        d.leaseToken(),
        d.context().correlationId(),
        false,
        retryable,
        Map.of(),
        error);
  }
}
