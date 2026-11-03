package io.flowlab.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "flowlab.kafka-enabled", havingValue = "true")
public class KafkaConfiguration {
  @Bean
  KafkaAdmin.NewTopics topics() {
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name("flowlab.tasks.commands").partitions(3).replicas(1).build(),
        TopicBuilder.name("flowlab.tasks.results").partitions(3).replicas(1).build(),
        TopicBuilder.name("flowlab.tasks.dlt").partitions(3).replicas(1).build());
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template, (record, e) -> new TopicPartition("flowlab.tasks.dlt", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 2));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
