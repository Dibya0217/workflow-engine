package io.flowlab.task;

import java.util.Map;
import java.util.UUID;

public record TaskContext(
    UUID executionId,
    UUID taskId,
    String taskName,
    String taskType,
    int attempt,
    String idempotencyKey,
    String correlationId,
    Map<String, Object> input,
    Map<String, Object> config) {}
