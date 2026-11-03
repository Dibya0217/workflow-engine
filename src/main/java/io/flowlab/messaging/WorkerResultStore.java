package io.flowlab.messaging;

import io.flowlab.engine.*;
import io.flowlab.persistence.DatabaseSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerResultStore {
  private final DatabaseSupport db;

  public WorkerResultStore(DatabaseSupport db) {
    this.db = db;
  }

  public boolean exists(Dispatch command) {
    return Boolean.TRUE.equals(
        db.jdbc.queryForObject(
            "select exists(select 1 from worker_results where dispatch_token=?)",
            Boolean.class,
            command.leaseToken()));
  }

  @Transactional
  public boolean save(TaskOutcome result) {
    int inserted =
        db.jdbc.update(
            "insert into worker_results(dispatch_token,result) values (?,?::jsonb) on conflict do"
                + " nothing",
            result.leaseToken(),
            db.json(result));
    if (inserted == 0) return false;
    db.outbox("flowlab.tasks.results", result.executionId().toString(), "TASK_RESULT", result);
    return true;
  }
}
