package io.flowlab;

import io.flowlab.definition.WorkflowService;
import io.flowlab.domain.*;
import io.flowlab.execution.ExecutionService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.boot.SpringApplication;

/**
 * Explicit local demonstration harness; uses real PostgreSQL and real engine executions. Never
 * production.
 */
public class LocalDemo {
  public static void main(String[] args) throws Exception {
    var postgres = EmbeddedPostgres.builder().setPort(0).start();
    byte[] random = new byte[36];
    new SecureRandom().nextBytes(random);
    String password = HexFormat.of().formatHex(random);
    System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
    System.setProperty("spring.datasource.username", "postgres");
    System.setProperty("spring.datasource.password", "");
    System.setProperty("flowlab.jwt-secret", password + password);
    System.setProperty("flowlab.bootstrap-username", "flowlab-demo");
    System.setProperty("flowlab.bootstrap-password", password);
    System.setProperty("flowlab.kafka-enabled", "false");
    var app = SpringApplication.run(FlowLabApplication.class, args);
    Files.createDirectories(Path.of(".cache"));
    Path login = Path.of(".cache/demo-login.json");
    Files.writeString(
        login,
        "{\"username\":\"flowlab-demo\",\"password\":\"" + password + "\"}",
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    Files.setPosixFilePermissions(login, PosixFilePermissions.fromString("rw-------"));
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  app.close();
                  try {
                    postgres.close();
                    Files.deleteIfExists(login);
                  } catch (Exception e) {
                    System.err.println("Demo cleanup: " + e.getMessage());
                  }
                }));
    var workflows = app.getBean(WorkflowService.class);
    var runs = app.getBean(ExecutionService.class);
    var order =
        workflows.create(
            new WorkflowSpec(
                "order-processing",
                1,
                "Validate orders, process payments, and keep inventory in sync.",
                List.of(
                    new TaskDefinition(
                        "validate-order",
                        "VALIDATION",
                        "process-payment",
                        null,
                        Map.of("requiredFields", List.of("orderId"))),
                    new TaskDefinition(
                        "process-payment",
                        "PAYMENT",
                        "update-inventory",
                        new RetryPolicy(3, 1000, 2, 30000),
                        Map.of()),
                    new TaskDefinition(
                        "update-inventory", "INVENTORY", "send-notification", null, Map.of()),
                    new TaskDefinition(
                        "send-notification", "NOTIFICATION", null, null, Map.of()))));
    workflows.activate(order.id, true);
    for (int i = 1; i <= 5; i++)
      runs.start(order.id, Map.of("orderId", "DEMO-" + i), ExecutionMode.LOCAL, null);
    var notifications =
        workflows.create(
            new WorkflowSpec(
                "customer-notifications",
                1,
                "Deliver customer updates with automatic exponential retries.",
                List.of(
                    new TaskDefinition(
                        "validate-recipient",
                        "VALIDATION",
                        "notify",
                        null,
                        Map.of("requiredFields", List.of("email"))),
                    new TaskDefinition(
                        "notify",
                        "NOTIFICATION",
                        null,
                        new RetryPolicy(3, 1000, 2, 30000),
                        Map.of()))));
    workflows.activate(notifications.id, true);
    runs.start(
        notifications.id,
        Map.of(),
        ExecutionMode.LOCAL,
        null); // Real validation failure for inspection.
    var inventory =
        workflows.create(
            new WorkflowSpec(
                "inventory-reconciliation",
                1,
                "Check inventory input and persist a reconciliation result.",
                List.of(
                    new TaskDefinition("validate-input", "VALIDATION", "reconcile", null, Map.of()),
                    new TaskDefinition("reconcile", "INVENTORY", null, null, Map.of()))));
    workflows.activate(inventory.id, true);
    runs.start(inventory.id, Map.of("warehouse", "demo"), ExecutionMode.LOCAL, null);
    System.out.println(
        "FlowLab local demo is ready. Credentials: .cache/demo-login.json (generated; local"
            + " only).");
    new java.util.concurrent.CountDownLatch(1).await();
  }
}
