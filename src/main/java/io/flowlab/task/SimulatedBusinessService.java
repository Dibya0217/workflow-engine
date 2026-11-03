package io.flowlab.task;

import io.flowlab.persistence.DatabaseSupport;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database record is the simulated provider side effect, not a claim about real remote APIs.
 */
@Service
public class SimulatedBusinessService {
  private final DatabaseSupport db;

  public SimulatedBusinessService(DatabaseSupport db) {
    this.db = db;
  }

  @Transactional
  public TaskResult execute(String type, TaskContext context) {
    db.jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, context.idempotencyKey());
    var previous =
        db.jdbc.query(
            "select result::text from idempotency_records where idempotency_key=?",
            (rs, n) -> rs.getString(1),
            context.idempotencyKey());
    if (!previous.isEmpty()) return db.read(previous.getFirst(), TaskResult.class);
    if (Boolean.TRUE.equals(context.config().get("permanentFailure")))
      throw new TaskFailure("Simulated permanent " + type + " failure", false);
    Object failures = context.config().getOrDefault("failAttempts", 0);
    if (failures instanceof Number n && context.attempt() <= n.intValue())
      throw new TaskFailure("Simulated temporary " + type + " failure", true);
    var result =
        new TaskResult(
            Map.of(
                type.toLowerCase(Locale.ROOT) + "Reference",
                UUID.randomUUID().toString(),
                type.toLowerCase(Locale.ROOT) + "Status",
                "SUCCEEDED"));
    db.jdbc.update(
        "insert into idempotency_records(idempotency_key,task_type,result) values (?,?,?::jsonb)",
        context.idempotencyKey(),
        type,
        db.json(result));
    return result;
  }
}
