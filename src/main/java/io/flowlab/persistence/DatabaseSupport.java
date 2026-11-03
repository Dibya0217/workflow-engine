package io.flowlab.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSupport {
  public final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public DatabaseSupport(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Instant now() {
    return jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
  }

  public String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize data", e);
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid persisted event", e);
    }
  }

  public void event(UUID executionId, UUID taskId, String type, Map<String, ?> details) {
    jdbc.update(
        "INSERT INTO execution_events(workflow_execution_id,task_execution_id,event_type,details)"
            + " VALUES (?,?,?,?::jsonb)",
        executionId,
        taskId,
        type,
        json(details));
  }

  public void outbox(String topic, String key, String type, Object payload) {
    jdbc.update(
        "INSERT INTO outbox_events(id,event_type,topic,message_key,payload) VALUES"
            + " (?,?,?,?,?::jsonb)",
        UUID.randomUUID(),
        type,
        topic,
        key,
        json(payload));
  }
}
