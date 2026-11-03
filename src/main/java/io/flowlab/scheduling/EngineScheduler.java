package io.flowlab.scheduling;

import io.flowlab.domain.ExecutionMode;
import io.flowlab.engine.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flowlab.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EngineScheduler {
  private static final Logger log = LoggerFactory.getLogger(EngineScheduler.class);
  private final EngineTransactions engine;
  private final TaskInvoker invoker;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore capacity;

  public EngineScheduler(
      EngineTransactions engine,
      TaskInvoker invoker,
      @Value("${flowlab.worker-concurrency}") int concurrency) {
    if (concurrency < 1 || concurrency > 100)
      throw new IllegalArgumentException("Worker concurrency must be 1..100");
    this.engine = engine;
    this.invoker = invoker;
    this.capacity = new Semaphore(concurrency);
  }

  @Scheduled(fixedDelayString = "${flowlab.poll-ms}")
  public void tick() {
    for (int i = 0; i < 100 && capacity.tryAcquire(); i++) {
      boolean handedOff = false;
      try {
        var claim = engine.claim();
        if (claim.isEmpty()) return;
        var value = claim.get();
        if (value.mode() == ExecutionMode.LOCAL) {
          workers.submit(
              () -> {
                try {
                  engine.complete(invoker.invoke(value.dispatch()));
                } catch (Exception e) {
                  log.error(
                      "Worker failed; lease recovery will resume task {}",
                      value.dispatch().context().taskId(),
                      e);
                } finally {
                  capacity.release();
                }
              });
          handedOff = true;
        }
      } catch (Exception e) {
        log.error("Task claim failed; retrying next poll", e);
        return;
      } finally {
        if (!handedOff) capacity.release();
      }
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
