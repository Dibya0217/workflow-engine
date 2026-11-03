package io.flowlab.api;

import static io.flowlab.api.Views.*;

import io.flowlab.definition.WorkflowService;
import io.flowlab.domain.*;
import io.flowlab.execution.ExecutionService;
import io.flowlab.persistence.*;
import io.flowlab.task.TaskHandlerRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
  private final WorkflowService workflows;
  private final ExecutionService runs;
  private final WorkflowDefinitionRepository definitions;
  private final WorkflowExecutionRepository executions;
  private final TaskExecutionRepository tasks;
  private final DatabaseSupport db;
  private final TaskHandlerRegistry handlers;

  public ApiController(
      WorkflowService workflows,
      ExecutionService runs,
      WorkflowDefinitionRepository definitions,
      WorkflowExecutionRepository executions,
      TaskExecutionRepository tasks,
      DatabaseSupport db,
      TaskHandlerRegistry handlers) {
    this.workflows = workflows;
    this.runs = runs;
    this.definitions = definitions;
    this.executions = executions;
    this.tasks = tasks;
    this.db = db;
    this.handlers = handlers;
  }

  static Pageable page(int page, int size, String sort, String direction, Set<String> allowed) {
    if (page < 0 || size < 1 || size > 100 || !allowed.contains(sort))
      throw new IllegalArgumentException("Invalid pagination or sort field");
    return PageRequest.of(
        page, size, Sort.by(Sort.Direction.fromString(direction), sort).and(Sort.by("id")));
  }

  @PostMapping("/workflows")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public DefinitionView create(@Valid @RequestBody WorkflowSpec spec) {
    return DefinitionView.of(workflows.create(spec));
  }

  @PostMapping("/workflows/validate")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Boolean> validate(@Valid @RequestBody WorkflowSpec spec) {
    workflows.validate(spec);
    return Map.of("valid", true);
  }

  @PutMapping("/workflows/{id}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public DefinitionView version(@PathVariable UUID id, @Valid @RequestBody WorkflowSpec spec) {
    return DefinitionView.of(workflows.newVersion(id, spec));
  }

  @GetMapping("/workflows/{id}")
  public DefinitionView definition(@PathVariable UUID id) {
    return DefinitionView.of(workflows.get(id));
  }

  @GetMapping("/workflows")
  public PageView<DefinitionView> definitions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String direction,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active) {
    Specification<WorkflowDefinition> filter = (root, query, cb) -> cb.conjunction();
    if (q != null && !q.isBlank())
      filter =
          filter.and(
              (r, query, cb) ->
                  cb.like(cb.lower(r.get("name")), "%" + q.toLowerCase(Locale.ROOT) + "%"));
    if (active != null) filter = filter.and((r, query, cb) -> cb.equal(r.get("active"), active));
    return PageView.of(
        definitions.findAll(
            filter, page(page, size, sort, direction, Set.of("name", "version", "createdAt"))),
        DefinitionView::of);
  }

  @PostMapping("/workflows/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public DefinitionView activate(@PathVariable UUID id) {
    return DefinitionView.of(workflows.activate(id, true));
  }

  @PostMapping("/workflows/{id}/deactivate")
  @PreAuthorize("hasRole('ADMIN')")
  public DefinitionView deactivate(@PathVariable UUID id) {
    return DefinitionView.of(workflows.activate(id, false));
  }

  public record StartRequest(Map<String, Object> input, ExecutionMode mode) {
    public StartRequest {
      input = input == null ? Map.of() : input;
      mode = mode == null ? ExecutionMode.LOCAL : mode;
    }
  }

  @PostMapping("/workflows/{id}/executions")
  @ResponseStatus(HttpStatus.CREATED)
  public ExecutionView start(@PathVariable UUID id, @RequestBody StartRequest request) {
    return ExecutionView.of(
        runs.start(id, request.input(), request.mode(), org.slf4j.MDC.get("correlationId")));
  }

  @GetMapping("/executions")
  public PageView<ExecutionView> executions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String direction,
      @RequestParam(required = false) WorkflowStatus status,
      @RequestParam(required = false) UUID workflowDefinitionId) {
    Specification<WorkflowExecution> filter = (r, q, cb) -> cb.conjunction();
    if (status != null) filter = filter.and((r, q, cb) -> cb.equal(r.get("status"), status));
    if (workflowDefinitionId != null)
      filter =
          filter.and((r, q, cb) -> cb.equal(r.get("workflowDefinitionId"), workflowDefinitionId));
    return PageView.of(
        executions.findAll(
            filter, page(page, size, sort, direction, Set.of("createdAt", "updatedAt", "status"))),
        ExecutionView::of);
  }

  @GetMapping("/executions/{id}")
  public ExecutionView execution(@PathVariable UUID id) {
    return ExecutionView.of(runs.get(id));
  }

  @GetMapping("/executions/{id}/tasks")
  public List<TaskView> executionTasks(@PathVariable UUID id) {
    runs.get(id);
    return tasks.findByWorkflowExecutionIdOrderByPosition(id).stream().map(TaskView::of).toList();
  }

  @GetMapping("/executions/{id}/history")
  public PageView<Map<String, Object>> history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    runs.get(id);
    page(page, size, "id", "asc", Set.of("id"));
    var rows =
        db.jdbc.query(
            "select id,task_execution_id,event_type,details::text,created_at from execution_events"
                + " where workflow_execution_id=? order by id limit ? offset ?",
            (rs, n) -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", rs.getLong(1));
              row.put("taskExecutionId", rs.getObject(2));
              row.put("eventType", rs.getString(3));
              row.put("details", db.read(rs.getString(4), Map.class));
              row.put("createdAt", rs.getTimestamp(5).toInstant());
              return row;
            },
            id,
            size,
            (long) page * size);
    long total =
        db.jdbc.queryForObject(
            "select count(*) from execution_events where workflow_execution_id=?", Long.class, id);
    return new PageView<>(rows, page, size, total, (int) Math.ceil((double) total / size));
  }

  @PostMapping("/executions/{id}/{action:pause|resume|retry|cancel}")
  public ExecutionView control(@PathVariable UUID id, @PathVariable String action) {
    return ExecutionView.of(runs.control(id, action));
  }

  @GetMapping("/tasks")
  public PageView<TaskView> tasks(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) TaskStatus status) {
    Specification<TaskExecution> filter =
        (r, q, cb) -> status == null ? cb.conjunction() : cb.equal(r.get("status"), status);
    return PageView.of(
        tasks.findAll(filter, page(page, size, "createdAt", "desc", Set.of("createdAt"))),
        TaskView::of);
  }

  @GetMapping("/task-types")
  public Set<String> taskTypes() {
    return handlers.types();
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    var counts = new LinkedHashMap<String, Object>();
    counts.put("totalWorkflows", definitions.count());
    counts.put(
        "activeExecutions",
        executions.countByStatus(WorkflowStatus.RUNNING)
            + executions.countByStatus(WorkflowStatus.CREATED));
    counts.put("completedExecutions", executions.countByStatus(WorkflowStatus.COMPLETED));
    counts.put("failedExecutions", executions.countByStatus(WorkflowStatus.FAILED));
    counts.put("totalExecutions", executions.count());
    counts.put(
        "averageDurationMs",
        db.jdbc.queryForObject(
            "select coalesce(avg(extract(epoch from (completed_at-started_at))*1000),0) from"
                + " workflow_executions where status='COMPLETED'",
            Double.class));
    return counts;
  }
}
