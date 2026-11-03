package io.flowlab.messaging;

import io.flowlab.engine.*;
import io.flowlab.persistence.DatabaseSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowlab.kafka-enabled", havingValue = "true")
public class KafkaWorkers {
  private final EngineTransactions engine;
  private final TaskInvoker invoker;
  private final WorkerResultStore results;
  private final DatabaseSupport db;

  public KafkaWorkers(
      EngineTransactions engine,
      TaskInvoker invoker,
      WorkerResultStore results,
      DatabaseSupport db) {
    this.engine = engine;
    this.invoker = invoker;
    this.results = results;
    this.db = db;
  }

  @KafkaListener(topics = "flowlab.tasks.commands", groupId = "flowlab-workers-v1")
  public void command(String payload) {
    var command = db.read(payload, Dispatch.class);
    if (command.eventVersion() != 1 || command.context() == null || command.leaseToken() == null)
      throw new IllegalArgumentException("Invalid command envelope");
    if (results.exists(command) || !engine.isCurrent(command)) return;
    // Re-delivery after a process crash may invoke again: business keys, not Kafka offsets,
    // deduplicate side effects.
    results.save(invoker.invoke(command));
  }

  @KafkaListener(topics = "flowlab.tasks.results", groupId = "flowlab-results-v1")
  public void result(String payload) {
    engine.complete(db.read(payload, TaskOutcome.class));
  }
}
