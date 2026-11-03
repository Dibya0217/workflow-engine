package io.flowlab;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowlab.persistence.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresSupport.properties(registry);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder passwords;

  @BeforeEach
  void provision() {
    if (users.findByUsername("api-admin").isEmpty()) {
      for (String role : List.of("ADMIN", "USER")) {
        var user = new AppUser();
        user.username = "api-" + role.toLowerCase();
        user.passwordHash = passwords.encode("integration-test-password");
        user.role = role;
        user.createdAt = Instant.now();
        users.save(user);
      }
    }
  }

  String token(String username) throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", "integration-test-password"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("accessToken").asText();
  }

  String spec() throws Exception {
    return json.writeValueAsString(
        Map.of(
            "name",
            "api-" + UUID.randomUUID(),
            "version",
            1,
            "description",
            "API test",
            "tasks",
            List.of(Map.of("name", "validate", "type", "VALIDATION"))));
  }

  @Test
  void rejectsUnauthenticatedRequests() throws Exception {
    mvc.perform(get("/api/v1/workflows")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsBadLoginWithoutLeakingUserExistence() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"missing\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("detail").value("Invalid credentials"));
  }

  @Test
  void userCanReadButCannotManageDefinitions() throws Exception {
    String token = token("api-user");
    mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/workflows")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(spec()))
        .andExpect(status().isForbidden());
  }

  @Test
  void createsActivatesAndStartsWorkflowThroughRealSecurityChain() throws Exception {
    String token = token("api-admin");
    var response =
        mvc.perform(
                post("/api/v1/workflows")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(spec()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(response).get("id").asText();
    mvc.perform(
            post("/api/v1/workflows/" + id + "/executions")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"input\":{}}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/workflows/" + id + "/activate")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/workflows/" + id + "/executions")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"input\":{}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("status").value("CREATED"));
  }

  @Test
  void rejectsInvalidPaginationAndUnknownJsonFields() throws Exception {
    String token = token("api-admin");
    mvc.perform(get("/api/v1/workflows?size=500").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/workflows")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"unexpected\":true}"))
        .andExpect(status().isBadRequest());
  }
}
