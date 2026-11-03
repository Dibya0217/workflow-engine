package io.flowlab.definition;

import io.flowlab.domain.WorkflowSpec;
import io.flowlab.persistence.*;
import io.flowlab.task.TaskHandlerRegistry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowService {
  private final WorkflowDefinitionRepository definitions;
  private final TaskHandlerRegistry registry;
  private final DatabaseSupport db;

  public WorkflowService(
      WorkflowDefinitionRepository definitions, TaskHandlerRegistry registry, DatabaseSupport db) {
    this.definitions = definitions;
    this.registry = registry;
    this.db = db;
  }

  public void validate(WorkflowSpec spec) {
    DefinitionValidator.validate(spec, registry.types());
  }

  public WorkflowDefinition get(UUID id) {
    return definitions
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
  }

  @Transactional
  public WorkflowDefinition create(WorkflowSpec spec) {
    validate(spec);
    if (definitions.existsByNameAndVersion(spec.name(), spec.version()))
      throw new IllegalStateException("Workflow version already exists");
    var definition = new WorkflowDefinition();
    definition.name = spec.name();
    definition.version = spec.version();
    definition.description = spec.description();
    definition.specification = spec;
    definition.createdAt = db.now();
    return definitions.saveAndFlush(definition);
  }

  @Transactional
  public WorkflowDefinition newVersion(UUID id, WorkflowSpec spec) {
    WorkflowDefinition previous = get(id);
    if (!previous.name.equals(spec.name()) || spec.version() <= previous.version)
      throw new IllegalArgumentException(
          "A new version must retain the workflow name and increase its version");
    return create(spec);
  }

  @Transactional
  public WorkflowDefinition activate(UUID id, boolean active) {
    var definition =
        definitions
            .lock(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    definition.active = active;
    return definition;
  }
}
