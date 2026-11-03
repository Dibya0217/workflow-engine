package io.flowlab.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByUsername(String username);
}
