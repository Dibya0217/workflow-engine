package io.flowlab.api;

import static io.flowlab.api.Views.*;

import io.flowlab.domain.ExecutionMode;
import io.flowlab.persistence.*;
import io.flowlab.scheduling.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
  private final ScheduleService service;
  private final WorkflowScheduleRepository schedules;
  private final boolean kafka;
  private final long lease;
  private final int concurrency;

  public OperationsController(
      ScheduleService service,
      WorkflowScheduleRepository schedules,
      @Value("${flowlab.kafka-enabled}") boolean kafka,
      @Value("${flowlab.lease-seconds}") long lease,
      @Value("${flowlab.worker-concurrency}") int concurrency) {
    this.service = service;
    this.schedules = schedules;
    this.kafka = kafka;
    this.lease = lease;
    this.concurrency = concurrency;
  }

  public record NewSchedule(
      @NotNull UUID workflowDefinitionId,
      @NotBlank @Size(max = 100) String name,
      @Min(60) @Max(31536000) long intervalSeconds,
      @NotNull ExecutionMode mode,
      Map<String, Object> input) {
    public NewSchedule {
      input = input == null ? Map.of() : input;
    }
  }

  @GetMapping("/schedules")
  public PageView<ScheduleView> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageView.of(
        schedules.findAll(ApiController.page(page, size, "createdAt", "desc", Set.of("createdAt"))),
        ScheduleView::of);
  }

  @PostMapping("/schedules")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public ScheduleView create(@Valid @RequestBody NewSchedule request) {
    return ScheduleView.of(
        service.create(
            request.workflowDefinitionId(),
            request.name(),
            request.intervalSeconds(),
            request.mode(),
            request.input()));
  }

  @PostMapping("/schedules/{id}/{action:enable|disable}")
  @PreAuthorize("hasRole('ADMIN')")
  public ScheduleView control(@PathVariable UUID id, @PathVariable String action) {
    return ScheduleView.of(service.enable(id, action.equals("enable")));
  }

  @GetMapping("/settings")
  public Map<String, Object> settings() {
    return Map.of(
        "kafkaEnabled",
        kafka,
        "leaseSeconds",
        lease,
        "workerConcurrency",
        concurrency,
        "coordination",
        "PostgreSQL",
        "deliverySemantics",
        "At least once with fenced completion",
        "version",
        "1.0.0");
  }
}
