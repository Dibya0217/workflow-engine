package io.flowlab.recovery;

import io.flowlab.engine.EngineTransactions;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flowlab.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecoveryManager {
  private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);
  private final EngineTransactions engine;

  public RecoveryManager(EngineTransactions engine) {
    this.engine = engine;
  }

  @Scheduled(initialDelay = 1000, fixedDelay = 1000)
  public void recover() {
    try {
      for (int i = 0; i < 100 && engine.recoverOne(); i++) {
        /* bounded recovery batch */
      }
    } catch (Exception e) {
      log.error("Recovery scan failed; durable leases remain eligible", e);
    }
  }
}
