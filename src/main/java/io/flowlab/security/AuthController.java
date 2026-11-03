package io.flowlab.security;

import io.flowlab.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder jwt;
  private final long ttl;
  private final String dummyHash;

  public AuthController(
      AppUserRepository users,
      PasswordEncoder passwords,
      JwtEncoder jwt,
      @Value("${flowlab.jwt-ttl-seconds}") long ttl) {
    this.users = users;
    this.passwords = passwords;
    this.jwt = jwt;
    this.ttl = ttl;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public record Login(
      @NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 72) String password) {}

  public record NewUser(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,80}") String username,
      @NotBlank @Size(min = 12, max = 72) String password,
      @Pattern(regexp = "ADMIN|USER") @NotNull String role) {}

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody Login request) {
    var user = users.findByUsername(request.username()).orElse(null);
    boolean matches =
        passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash);
    if (user == null || !matches)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("flowlab")
            .subject(user.username)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttl))
            .claim("roles", List.of(user.role))
            .build();
    String token =
        jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return Map.of(
        "accessToken", token, "expiresIn", ttl, "username", user.username, "role", user.role);
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public Map<String, Object> user(@Valid @RequestBody NewUser request) {
    var user = new AppUser();
    user.username = request.username();
    user.passwordHash = passwords.encode(request.password());
    user.role = request.role();
    user.createdAt = Instant.now();
    users.saveAndFlush(user);
    return Map.of("id", user.id, "username", user.username, "role", user.role);
  }

  @GetMapping("/me")
  public Map<String, Object> me(Authentication authentication) {
    return Map.of(
        "username", authentication.getName(), "authorities", authentication.getAuthorities());
  }
}
