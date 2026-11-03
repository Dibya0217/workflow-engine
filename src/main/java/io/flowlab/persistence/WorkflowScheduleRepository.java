package io.flowlab.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WorkflowScheduleRepository extends JpaRepository<WorkflowSchedule, UUID> {
  @Query(
      value =
          "SELECT * FROM workflow_schedules WHERE enabled AND next_run_at <= clock_timestamp()"
              + " ORDER BY next_run_at LIMIT 1 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<WorkflowSchedule> due();
}
