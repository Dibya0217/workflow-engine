package io.flowlab.scheduling;

import io.flowlab.domain.ExecutionMode;
import io.flowlab.execution.ExecutionService;
import io.flowlab.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {
  private final WorkflowScheduleRepository schedules;
  private final WorkflowDefinitionRepository definitions;
  private final ExecutionService runs;
  private final DatabaseSupport db;
  private final boolean kafkaEnabled;

  public ScheduleService(
      WorkflowScheduleRepository schedules,
      WorkflowDefinitionRepository definitions,
      ExecutionService runs,
      DatabaseSupport db,
      @Value("${flowlab.kafka-enabled}") boolean kafkaEnabled) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.runs = runs;
    this.db = db;
    this.kafkaEnabled = kafkaEnabled;
  }

  @Transactional
  public WorkflowSchedule create(
      UUID definitionId, String name, long seconds, ExecutionMode mode, Map<String, Object> input) {
    if (mode == ExecutionMode.KAFKA && !kafkaEnabled)
      throw new IllegalArgumentException("Kafka is disabled");
    if (!definitions.existsById(definitionId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
    var schedule = new WorkflowSchedule();
    schedule.workflowDefinitionId = definitionId;
    schedule.name = name;
    schedule.intervalSeconds = seconds;
    schedule.mode = mode;
    schedule.inputData = input;
    schedule.enabled = true;
    schedule.createdAt = db.now();
    schedule.updatedAt = schedule.createdAt;
    schedule.nextRunAt = schedule.createdAt.plusSeconds(seconds);
    return schedules.save(schedule);
  }

  @Transactional
  public WorkflowSchedule enable(UUID id, boolean enabled) {
    var schedule =
        schedules
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
    schedule.enabled = enabled;
    schedule.updatedAt = db.now();
    if (enabled) schedule.nextRunAt = schedule.updatedAt.plusSeconds(schedule.intervalSeconds);
    return schedule;
  }

  @Transactional
  public boolean tick() {
    var due = schedules.due();
    if (due.isEmpty()) return false;
    var schedule = due.get();
    Instant now = db.now();
    var definition = definitions.lock(schedule.workflowDefinitionId).orElseThrow();
    if (definition.active)
      runs.start(definition.id, schedule.inputData, schedule.mode, "schedule-" + schedule.id);
    schedule.nextRunAt = now.plusSeconds(schedule.intervalSeconds);
    schedule.updatedAt = now;
    return true;
  }
}
