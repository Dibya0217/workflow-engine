package io.flowlab;

import static org.assertj.core.api.Assertions.*;

import io.flowlab.task.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TaskHandlerTest {
  @Test
  void validatesRequiredFields() {
    var context =
        new TaskContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "check",
            "VALIDATION",
            1,
            "key",
            "correlation",
            Map.of(),
            Map.of("requiredFields", List.of("orderId")));
    assertThatThrownBy(() -> new ValidationTaskHandler().execute(context))
        .isInstanceOf(TaskFailure.class)
        .hasMessageContaining("orderId");
  }

  @Test
  void registryRejectsDuplicateTypes() {
    assertThatThrownBy(
            () ->
                new TaskHandlerRegistry(
                    List.of(new ValidationTaskHandler(), new ValidationTaskHandler())))
        .isInstanceOf(IllegalStateException.class);
  }
}
