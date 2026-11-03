package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkflowExecutionRepository
    extends JpaRepository<WorkflowExecution, UUID>, JpaSpecificationExecutor<WorkflowExecution> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from WorkflowExecution e where e.id=:id")
  Optional<WorkflowExecution> lock(@Param("id") UUID id);

  @Query(
      value =
          """
          SELECT e.* FROM workflow_executions e JOIN task_executions t
          ON t.workflow_execution_id=e.id AND t.task_name=e.current_task
          WHERE e.status IN ('CREATED','RUNNING') AND (:kafka OR e.mode='LOCAL')
          AND (t.status='PENDING' OR (t.status='RETRY_WAIT' AND t.retry_due_at<=clock_timestamp()))
          ORDER BY e.created_at LIMIT 1 FOR UPDATE OF e SKIP LOCKED
          """,
      nativeQuery = true)
  Optional<WorkflowExecution> claimable(@Param("kafka") boolean kafka);

  @Query(
      value =
          """
          SELECT e.* FROM workflow_executions e JOIN task_executions t ON t.workflow_execution_id=e.id
          WHERE e.status IN ('RUNNING','PAUSED') AND t.status='RUNNING' AND t.lease_until<=clock_timestamp()
          ORDER BY t.lease_until LIMIT 1 FOR UPDATE OF e SKIP LOCKED
          """,
      nativeQuery = true)
  Optional<WorkflowExecution> expired();

  long countByStatus(WorkflowStatus status);
}
