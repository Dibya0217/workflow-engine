package io.flowlab.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TaskExecutionRepository
    extends JpaRepository<TaskExecution, UUID>, JpaSpecificationExecutor<TaskExecution> {
  List<TaskExecution> findByWorkflowExecutionIdOrderByPosition(UUID executionId);

  Optional<TaskExecution> findByWorkflowExecutionIdAndTaskName(UUID executionId, String taskName);
}
