package io.flowlab.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record WorkflowSpec(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,100}") String name,
    @Min(1) int version,
    @Size(max = 2000) String description,
    @NotEmpty @Size(max = 100) List<@Valid TaskDefinition> tasks) {
  public WorkflowSpec {
    if (tasks != null) tasks = List.copyOf(tasks);
  }

  public TaskDefinition task(String name) {
    return tasks.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
  }
}
