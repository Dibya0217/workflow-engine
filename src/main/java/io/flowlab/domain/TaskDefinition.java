package io.flowlab.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;

public record TaskDefinition(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}") String name,
    @NotBlank @Size(max = 80) String type,
    @Size(max = 80) String next,
    @Valid RetryPolicy retryPolicy,
    Map<String, Object> config) {
  public TaskDefinition {
    if (retryPolicy == null) retryPolicy = RetryPolicy.defaults();
    config = config == null ? Map.of() : Map.copyOf(config);
  }
}
