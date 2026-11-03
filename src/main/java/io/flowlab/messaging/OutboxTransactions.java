package io.flowlab.messaging;

import io.flowlab.persistence.DatabaseSupport;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxTransactions {
  private final DatabaseSupport db;

  public OutboxTransactions(DatabaseSupport db) {
    this.db = db;
  }

  public record Message(UUID id, String topic, String key, String payload, UUID token) {}

  @Transactional
  public Optional<Message> claim() {
    UUID token = UUID.randomUUID();
    var rows =
        db.jdbc.query(
            """
            WITH candidate AS (SELECT id FROM outbox_events WHERE published_at IS NULL AND due_at<=clock_timestamp()
            AND (lease_until IS NULL OR lease_until<=clock_timestamp()) ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
            UPDATE outbox_events e SET lease_token=?,lease_until=clock_timestamp()+interval '30 seconds',publish_attempts=publish_attempts+1
            FROM candidate c WHERE e.id=c.id RETURNING e.id,e.topic,e.message_key,e.payload::text
            """,
            (rs, n) ->
                new Message(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    token),
            token);
    return rows.stream().findFirst();
  }

  @Transactional
  public void published(Message message) {
    db.jdbc.update(
        "update outbox_events set"
            + " published_at=clock_timestamp(),lease_token=null,lease_until=null,last_error=null"
            + " where id=? and lease_token=?",
        message.id(),
        message.token());
  }

  @Transactional
  public void failed(Message message, Exception failure) {
    String error = failure.getClass().getSimpleName();
    db.jdbc.update(
        """
        update outbox_events set lease_token=null,lease_until=null,last_error=?,
        due_at=clock_timestamp()+make_interval(secs=>least(60,power(2,least(publish_attempts,6)))::int)
        where id=? and lease_token=?
        """,
        error,
        message.id(),
        message.token());
  }
}
