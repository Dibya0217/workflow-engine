package io.flowlab.scheduling;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flowlab.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulePoller {
  private static final Logger log = LoggerFactory.getLogger(SchedulePoller.class);
  private final ScheduleService schedules;

  public SchedulePoller(ScheduleService schedules) {
    this.schedules = schedules;
  }

  @Scheduled(fixedDelay = 1000)
  public void tick() {
    try {
      for (int i = 0; i < 20 && schedules.tick(); i++) {}
    } catch (Exception e) {
      log.error("Schedule scan failed", e);
    }
  }
}
