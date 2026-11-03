package io.flowlab.persistence;

import io.flowlab.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id public UUID id = UUID.randomUUID();
  @Version public long rowVersion;

  @Column(nullable = false, unique = true)
  public String username;

  public String passwordHash;
  public String role;
  public Instant createdAt;
}
