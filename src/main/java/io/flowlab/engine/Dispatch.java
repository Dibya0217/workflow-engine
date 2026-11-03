package io.flowlab.engine;

import io.flowlab.task.TaskContext;
import java.util.UUID;

public record Dispatch(int eventVersion, UUID eventId, UUID leaseToken, TaskContext context) {}
