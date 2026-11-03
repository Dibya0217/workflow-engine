package io.flowlab;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/** Uses native real PostgreSQL by default; -Dflowlab.test.containers=true selects Docker. */
public final class PostgresSupport {
  private static EmbeddedPostgres embedded;
  private static PostgreSQLContainer<?> container;
  private static String url, username, password;

  static {
    try {
      if (Boolean.getBoolean("flowlab.test.containers")) {
        container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        url = container.getJdbcUrl();
        username = container.getUsername();
        password = container.getPassword();
      } else {
        embedded = EmbeddedPostgres.builder().setPort(0).start();
        url = embedded.getJdbcUrl("postgres", "postgres");
        username = "postgres";
        password = "";
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
  }
}
