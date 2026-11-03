package io.flowlab;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.flowlab.definition.WorkflowService;
import io.flowlab.domain.*;
import io.flowlab.engine.*;
import io.flowlab.execution.ExecutionService;
import io.flowlab.messaging.*;
import io.flowlab.persistence.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@SpringBootTest(properties = {"flowlab.kafka-enabled=true"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
    partitions = 3,
    topics = {"flowlab.tasks.commands", "flowlab.tasks.results", "flowlab.tasks.dlt"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresSupport.properties(registry);
  }

  @Autowired WorkflowService definitions;
  @Autowired ExecutionService runs;
  @Autowired EngineTransactions engine;
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired DatabaseSupport db;
  @Autowired TaskExecutionRepository tasks;
  @Autowired OutboxTransactions outbox;
  @Autowired org.springframework.kafka.test.EmbeddedKafkaBroker broker;

  @BeforeEach
  void clean() {
    db.jdbc.execute(
        "TRUNCATE"
            + " workflow_schedules,worker_results,outbox_events,idempotency_records,execution_events,task_executions,workflow_executions,workflow_definitions"
            + " CASCADE");
  }

  @Test
  void poisonCommandIsPublishedToDeadLetterTopic() throws Exception {
    var properties =
        org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps(
            "dlt-" + UUID.randomUUID(), "false", broker);
    try (var consumer =
        new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                properties,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())
            .createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, "flowlab.tasks.dlt");
      kafka.send("flowlab.tasks.commands", "poison", "{broken-json").get();
      var deadLetter =
          org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord(
              consumer, "flowlab.tasks.dlt", Duration.ofSeconds(20));
      assertThat(deadLetter.value()).isEqualTo("{broken-json");
      assertThat(deadLetter.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
    }
  }

  @Test
  void outboxDeliversCommandsAndResultsAndDuplicateCommandIsHarmless() throws Exception {
    var definition =
        definitions.create(
            new WorkflowSpec(
                "kafka-order",
                1,
                "Kafka test",
                List.of(new TaskDefinition("payment", "PAYMENT", null, null, Map.of()))));
    definitions.activate(definition.id, true);
    var run =
        runs.start(definition.id, Map.of("orderId", "K-100"), ExecutionMode.KAFKA, "kafka-it");
    var dispatch = engine.claim().orElseThrow().dispatch();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> assertThat(runs.get(run.id).status).isEqualTo(WorkflowStatus.COMPLETED));
    kafka.send("flowlab.tasks.commands", run.id.toString(), db.json(dispatch)).get();
    kafka.send("flowlab.tasks.commands", run.id.toString(), db.json(dispatch)).get();
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(db.jdbc.queryForObject("select count(*) from worker_results", Long.class))
                  .isEqualTo(1);
              assertThat(
                      db.jdbc.queryForObject(
                          "select count(*) from idempotency_records", Long.class))
                  .isEqualTo(1);
              assertThat(
                      tasks
                          .findByWorkflowExecutionIdOrderByPosition(run.id)
                          .getFirst()
                          .attemptCount)
                  .isEqualTo(1);
            });
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        db.jdbc.queryForObject(
                            "select count(*) from outbox_events where published_at is null",
                            Long.class))
                    .isZero());
  }
}
