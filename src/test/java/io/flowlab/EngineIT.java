package io.flowlab;

import static org.assertj.core.api.Assertions.*;

import io.flowlab.definition.WorkflowService;
import io.flowlab.domain.*;
import io.flowlab.engine.*;
import io.flowlab.execution.ExecutionService;
import io.flowlab.persistence.*;
import io.flowlab.retry.RetryManager;
import io.flowlab.task.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class EngineIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresSupport.properties(registry);
  }

  @Autowired WorkflowService workflows;
  @Autowired ExecutionService runs;
  @Autowired EngineTransactions engine;
  @Autowired TaskInvoker invoker;
  @Autowired WorkflowExecutionRepository executions;
  @Autowired WorkflowDefinitionRepository definitions;
  @Autowired TaskExecutionRepository tasks;
  @Autowired DatabaseSupport db;
  @Autowired RetryManager retries;
  @Autowired MeterRegistry metrics;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired SimulatedBusinessService business;

  @BeforeEach
  void clean() {
    db.jdbc.execute(
        "TRUNCATE"
            + " workflow_schedules,worker_results,outbox_events,idempotency_records,execution_events,task_executions,workflow_executions,workflow_definitions"
            + " CASCADE");
  }

  WorkflowDefinition definition(Map<String, Object> config) {
    var spec =
        new WorkflowSpec(
            "order-" + UUID.randomUUID(),
            1,
            "Integration workflow",
            List.of(
                new TaskDefinition(
                    "validate",
                    "VALIDATION",
                    "payment",
                    null,
                    Map.of("requiredFields", List.of("orderId"))),
                new TaskDefinition(
                    "payment", "PAYMENT", "notify", new RetryPolicy(3, 1, 2, 10), config),
                new TaskDefinition("notify", "NOTIFICATION", null, null, Map.of())));
    var d = workflows.create(spec);
    workflows.activate(d.id, true);
    return d;
  }

  WorkflowExecution start(Map<String, Object> config) {
    return runs.start(
        definition(config).id, Map.of("orderId", "O-100"), ExecutionMode.LOCAL, "integration");
  }

  Dispatch claim() {
    return engine.claim().orElseThrow().dispatch();
  }

  void executeOne() {
    Dispatch d = claim();
    assertThat(engine.complete(invoker.invoke(d))).isTrue();
  }

  void expire(UUID taskId) {
    db.jdbc.update(
        "update task_executions set lease_until=clock_timestamp()-interval '1 second' where id=?",
        taskId);
  }

  void due(UUID taskId) {
    db.jdbc.update(
        "update task_executions set retry_due_at=clock_timestamp()-interval '1 second' where id=?",
        taskId);
  }

  @Test
  void nestedConfigurationSurvivesJsonbAndRequiredFieldIsEnforced() {
    var definition = definition(Map.of());
    var stored = workflows.get(definition.id).specification.task("validate").config();
    assertThat(stored.get("requiredFields")).isEqualTo(List.of("orderId"));
    var run = runs.start(definition.id, Map.of(), ExecutionMode.LOCAL, null);
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.FAILED);
    assertThat(tasks.findByWorkflowExecutionIdOrderByPosition(run.id).getFirst().errorMessage)
        .contains("orderId");
  }

  @Test
  void completesSequentialWorkflowAndPersistsOutput() {
    var run = start(Map.of());
    executeOne();
    executeOne();
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(runs.get(run.id).outputData)
        .containsKeys("orderId", "paymentReference", "notificationReference");
    assertThat(tasks.findByWorkflowExecutionIdOrderByPosition(run.id))
        .allMatch(t -> t.status == TaskStatus.COMPLETED && t.attemptCount == 1);
  }

  @Test
  void reconstructsEngineAfterInterruptionWithoutRepeatingCompletedTask() {
    var run = start(Map.of());
    executeOne();
    Dispatch interrupted = claim();
    // Provider committed its effect, but the process died before engine completion.
    TaskOutcome lostResult = invoker.invoke(interrupted);
    expire(interrupted.context().taskId());
    var reconstructed =
        new EngineTransactions(executions, definitions, tasks, db, retries, metrics, 30, false);
    var tx = new TransactionTemplate(transactionManager);
    assertThat(tx.<Boolean>execute(status -> reconstructed.recoverOne())).isTrue();
    due(interrupted.context().taskId());
    Dispatch recovered = tx.execute(status -> reconstructed.claim().orElseThrow().dispatch());
    assertThat(recovered.context().idempotencyKey())
        .isEqualTo(interrupted.context().idempotencyKey());
    assertThat(tx.<Boolean>execute(status -> reconstructed.complete(lostResult))).isFalse();
    assertThat(tx.<Boolean>execute(status -> reconstructed.complete(invoker.invoke(recovered))))
        .isTrue();
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.COMPLETED);
    var records = tasks.findByWorkflowExecutionIdOrderByPosition(run.id);
    assertThat(records.getFirst().attemptCount).isEqualTo(1);
    assertThat(records.get(1).attemptCount).isEqualTo(2);
    assertThat(
            db.jdbc.queryForObject(
                "select count(*) from idempotency_records where task_type='PAYMENT'", Long.class))
        .isEqualTo(1);
    assertThat(records.get(1).outputData).isEqualTo(lostResult.output());
  }

  @Test
  void retriesTransientFailureAndRejectsEarlyClaim() {
    var run = start(Map.of("failAttempts", 1));
    executeOne();
    Dispatch d = claim();
    engine.complete(invoker.invoke(d));
    assertThat(tasks.findById(d.context().taskId()).orElseThrow().status)
        .isEqualTo(TaskStatus.RETRY_WAIT);
    db.jdbc.update(
        "update task_executions set retry_due_at=clock_timestamp()+interval '1 hour' where id=?",
        d.context().taskId());
    assertThat(engine.claim()).isEmpty();
    due(d.context().taskId());
    executeOne();
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void permanentFailureRequiresExplicitRetryAndPreservesHistory() {
    var run = start(Map.of("permanentFailure", true));
    executeOne();
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.FAILED);
    runs.control(run.id, "retry");
    executeOne();
    assertThat(tasks.findByWorkflowExecutionIdOrderByPosition(run.id).get(1).attemptCount)
        .isEqualTo(2);
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void pauseAllowsInflightCompletionButGatesNextClaim() {
    var run = start(Map.of());
    Dispatch d = claim();
    runs.control(run.id, "pause");
    engine.complete(invoker.invoke(d));
    assertThat(engine.claim()).isEmpty();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.PAUSED);
    runs.control(run.id, "resume");
    executeOne();
    executeOne();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void cancellationFencesInflightResult() {
    var run = start(Map.of());
    var dispatch = claim();
    runs.control(run.id, "cancel");
    assertThat(engine.complete(invoker.invoke(dispatch))).isFalse();
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(engine.claim()).isEmpty();
  }

  @Test
  void duplicateCompletionCannotAdvanceTwice() {
    var run = start(Map.of());
    var outcome = invoker.invoke(claim());
    assertThat(engine.complete(outcome)).isTrue();
    assertThat(engine.complete(outcome)).isFalse();
    assertThat(runs.get(run.id).currentTask).isEqualTo("payment");
  }

  @Test
  void concurrentClaimsYieldOneOwner() throws Exception {
    start(Map.of());
    try (var pool = Executors.newFixedThreadPool(8)) {
      var gate = new CountDownLatch(1);
      var futures = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 8; i++)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return engine.claim().isPresent();
                }));
      gate.countDown();
      int owners = 0;
      for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) owners++;
      assertThat(owners).isEqualTo(1);
    }
  }

  @Test
  void immutableNewVersionDoesNotChangeExistingRun() {
    var original = definition(Map.of());
    var run = runs.start(original.id, Map.of("orderId", "x"), ExecutionMode.LOCAL, null);
    var next =
        new WorkflowSpec(
            original.name,
            2,
            "New version",
            List.of(new TaskDefinition("only", "VALIDATION", null, null, null)));
    var newer = workflows.newVersion(original.id, next);
    assertThat(newer.id).isNotEqualTo(original.id);
    assertThat(workflows.get(original.id).specification.tasks()).hasSize(3);
    executeOne();
    executeOne();
    executeOne();
    assertThat(runs.get(run.id).workflowVersion).isEqualTo(1);
  }

  @Test
  void concurrentProviderCallsShareOneResult() throws Exception {
    var run = start(Map.of());
    executeOne();
    var context = claim().context();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> business.execute("PAYMENT", context));
      var b = pool.submit(() -> business.execute("PAYMENT", context));
      assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void exhaustedExpiredLeaseFailsPermanently() {
    var run = start(Map.of());
    executeOne();
    for (int i = 0; i < 3; i++) {
      var d = claim();
      expire(d.context().taskId());
      engine.recoverOne();
      due(d.context().taskId());
    }
    assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.FAILED);
    assertThat(engine.claim()).isEmpty();
  }
}
