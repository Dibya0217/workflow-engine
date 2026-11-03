package io.flowlab.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository
    extends JpaRepository<WorkflowDefinition, UUID>, JpaSpecificationExecutor<WorkflowDefinition> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from WorkflowDefinition d where d.id=:id")
  Optional<WorkflowDefinition> lock(@Param("id") UUID id);

  boolean existsByNameAndVersion(String name, int version);
}
