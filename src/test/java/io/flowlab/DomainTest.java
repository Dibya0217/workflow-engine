package io.flowlab;

import static org.assertj.core.api.Assertions.*;

import io.flowlab.definition.DefinitionValidator;
import io.flowlab.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainTest {
  TaskDefinition task(String name, String next) {
    return new TaskDefinition(name, "VALIDATION", next, null, null);
  }

  WorkflowSpec spec(TaskDefinition... tasks) {
    return new WorkflowSpec("test", 1, "test", List.of(tasks));
  }

  @Test
  void acceptsSequentialGraph() {
    assertThatCode(
            () ->
                DefinitionValidator.validate(
                    spec(task("a", "b"), task("b", null)), Set.of("VALIDATION")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsCycle() {
    assertThatThrownBy(
            () -> DefinitionValidator.validate(spec(task("a", "a")), Set.of("VALIDATION")))
        .hasMessageContaining("cycle");
  }

  @Test
  void rejectsUnknownTransition() {
    assertThatThrownBy(
            () -> DefinitionValidator.validate(spec(task("a", "missing")), Set.of("VALIDATION")))
        .hasMessageContaining("Unknown next");
  }

  @Test
  void rejectsDisconnectedTasks() {
    assertThatThrownBy(
            () ->
                DefinitionValidator.validate(
                    spec(task("a", null), task("b", null)), Set.of("VALIDATION")))
        .hasMessageContaining("sequential");
  }

  @Test
  void rejectsDuplicateNames() {
    assertThatThrownBy(
            () ->
                DefinitionValidator.validate(
                    spec(task("a", null), task("a", null)), Set.of("VALIDATION")))
        .hasMessageContaining("unique");
  }

  @Test
  void rejectsUnknownHandler() {
    assertThatThrownBy(() -> DefinitionValidator.validate(spec(task("a", null)), Set.of()))
        .hasMessageContaining("Unknown task type");
  }

  @Test
  void terminalStatesCannotRestart() {
    assertThatThrownBy(() -> WorkflowStatus.COMPLETED.requireTransition(WorkflowStatus.RUNNING))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> TaskStatus.COMPLETED.requireTransition(TaskStatus.RUNNING))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void supportsPauseAndRetryTransitions() {
    WorkflowStatus.RUNNING.requireTransition(WorkflowStatus.PAUSED);
    WorkflowStatus.FAILED.requireTransition(WorkflowStatus.RUNNING);
    TaskStatus.RUNNING.requireTransition(TaskStatus.RETRY_WAIT);
    TaskStatus.RETRY_WAIT.requireTransition(TaskStatus.RUNNING);
  }

  @Test
  void exponentialBackoffIsCapped() {
    RetryPolicy policy = new RetryPolicy(5, 1000, 2, 5000);
    assertThat(policy.delayAfter(1)).isEqualTo(1000);
    assertThat(policy.delayAfter(3)).isEqualTo(4000);
    assertThat(policy.delayAfter(100)).isEqualTo(5000);
  }
}
