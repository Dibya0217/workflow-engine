package io.flowlab;

import static org.assertj.core.api.Assertions.*;

import io.flowlab.definition.WorkflowService;
import io.flowlab.domain.*;
import io.flowlab.engine.*;
import io.flowlab.execution.ExecutionService;
import io.flowlab.messaging.OutboxTransactions;
import io.flowlab.persistence.*;
import io.flowlab.scheduling.ScheduleService;
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
class OperationsIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresSupport.properties(registry);
  }

  @Autowired DatabaseSupport db;
  @Autowired OutboxTransactions outbox;
  @Autowired ScheduleService schedules;
  @Autowired WorkflowService definitions;
  @Autowired ExecutionService runs;
  @Autowired EngineTransactions engine;
  @Autowired PlatformTransactionManager manager;

  @BeforeEach
  void clean() {
    db.jdbc.execute(
        "TRUNCATE"
            + " workflow_schedules,worker_results,outbox_events,idempotency_records,execution_events,task_executions,workflow_executions,workflow_definitions"
            + " CASCADE");
  }

  @Test
  void failedOutboxPublicationIsDurableAndRescheduled() {
    db.outbox("test", "key", "TEST", Map.of("eventVersion", 1));
    var message = outbox.claim().orElseThrow();
    outbox.failed(message, new IllegalStateException("broker down"));
    assertThat(outbox.claim()).isEmpty();
    assertThat(
            db.jdbc.queryForObject(
                "select published_at is null from outbox_events where id=?",
                Boolean.class,
                message.id()))
        .isTrue();
    db.jdbc.update("update outbox_events set due_at=clock_timestamp()-interval '1 second'");
    var next = outbox.claim().orElseThrow();
    assertThat(next.id()).isEqualTo(message.id());
    outbox.published(message); // A stale publisher cannot mark a newer claim as sent.
    assertThat(
            db.jdbc.queryForObject(
                "select published_at is null from outbox_events where id=?",
                Boolean.class,
                message.id()))
        .isTrue();
    outbox.published(next);
    assertThat(outbox.claim()).isEmpty();
  }

  @Test
  void expiredRelayLeaseCanBeClaimedAgain() {
    db.outbox("test", "key", "TEST", Map.of());
    var before = outbox.claim().orElseThrow();
    db.jdbc.update("update outbox_events set lease_until=clock_timestamp()-interval '1 second'");
    var after = outbox.claim().orElseThrow();
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.token()).isNotEqualTo(before.token());
  }

  @Test
  void outboxIsRolledBackTogetherWithBusinessTransaction() {
    var tx = new TransactionTemplate(manager);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      db.outbox("test", "key", "TEST", Map.of());
                      throw new IllegalStateException("crash before commit");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(db.jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
  }

  @Test
  void concurrentScheduleTicksCreateOneRunAndCoalesceMissedIntervals() throws Exception {
    var definition =
        definitions.create(
            new WorkflowSpec(
                "scheduled",
                1,
                "schedule test",
                List.of(new TaskDefinition("check", "VALIDATION", null, null, null))));
    definitions.activate(definition.id, true);
    var schedule = schedules.create(definition.id, "hourly", 3600, ExecutionMode.LOCAL, Map.of());
    db.jdbc.update(
        "update workflow_schedules set next_run_at=clock_timestamp()-interval '2 days' where id=?",
        schedule.id);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var gate = new CountDownLatch(1);
      var jobs = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 4; i++)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  return schedules.tick();
                }));
      gate.countDown();
      for (var job : jobs) job.get(10, TimeUnit.SECONDS);
    }
    assertThat(db.jdbc.queryForObject("select count(*) from workflow_executions", Long.class))
        .isEqualTo(1);
    assertThat(schedules.tick()).isFalse();
  }
}
