package io.flowlab.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowlab.kafka-enabled", havingValue = "true")
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxTransactions outbox;
  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry metrics;

  public OutboxPublisher(
      OutboxTransactions outbox, KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelay = 250)
  public void publish() {
    for (int i = 0; i < 50; i++) {
      var claimed = outbox.claim();
      if (claimed.isEmpty()) return;
      var message = claimed.get();
      try {
        kafka.send(message.topic(), message.key(), message.payload()).get(20, TimeUnit.SECONDS);
        outbox.published(message);
        metrics.counter("flowlab.outbox.published").increment();
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        outbox.failed(message, e);
        log.warn("Outbox event {} deferred after publication failure", message.id(), e);
        metrics.counter("flowlab.outbox.failures").increment();
        return;
      }
    }
  }
}
