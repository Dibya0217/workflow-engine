package io.flowlab.security;

import io.flowlab.persistence.*;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements ApplicationRunner {
  private final AppUserRepository users;
  private final PasswordEncoder passwords;
  private final DatabaseSupport db;
  private final String username;
  private final String password;

  public BootstrapAdmin(
      AppUserRepository users,
      PasswordEncoder passwords,
      DatabaseSupport db,
      @Value("${flowlab.bootstrap-username}") String username,
      @Value("${flowlab.bootstrap-password}") String password) {
    this.users = users;
    this.passwords = passwords;
    this.db = db;
    this.username = username;
    this.password = password;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (username.isBlank() && password.isBlank()) return;
    if (!username.matches("[a-zA-Z0-9_.-]{3,80}")
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Bootstrap username must be valid; password must be 12..72 bytes");
    db.jdbc.query("select pg_advisory_xact_lock(73658234)", rs -> {});
    if (users.findByUsername(username).isEmpty()) {
      var user = new AppUser();
      user.username = username;
      user.passwordHash = passwords.encode(password);
      user.role = "ADMIN";
      user.createdAt = Instant.now();
      users.save(user);
    }
  }
}
