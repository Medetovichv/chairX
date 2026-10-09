package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AdminUserApiIntegrationTest {
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private record TestUser(UUID id, String auth) {}

    @BeforeEach
    void ensureTestDatabase() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");
    }

    private TestUser user(UUID role) {
        UUID id = UUID.randomUUID();
        String username = "api_" + id.toString().replace("-", "");
        String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode("test-password");
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, TRUE)
                """, id, username, hash, "API Test");
        jdbc.update("INSERT INTO security_user_roles (user_id, role_id) VALUES (?, ?)", id, role);
        String credentials = username + ":test-password";
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return new TestUser(id, authorization);
    }

    @Test
    void adminCanListUsersRolesAndFetchUserWithoutPasswordHash() throws Exception {
        TestUser admin = user(ADMIN);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));
        mvc.perform(get("/api/admin/users/" + admin.id())
                        .header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        mvc.perform(get("/api/admin/roles").header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'ADMIN')]").exists());
    }

    @Test
    void adminCanCreateAssignAndDeactivateUserWithAudit() throws Exception {
        TestUser admin = user(ADMIN);
        String username = "created_" + UUID.randomUUID().toString().replace("-", "");
        String json = "{\"username\":\"" + username
                + "\",\"password\":\"StrongPassword123!\",\"displayName\":\"New Employee\"}";
        String location = mvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, admin.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        UUID targetId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(post("/api/admin/users/" + targetId + "/roles")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, admin.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + EMPLOYEE + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/users/" + targetId)
                        .header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"));

        mvc.perform(delete("/api/admin/users/" + targetId + "/roles/" + EMPLOYEE)
                        .with(csrf()).header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/admin/users/" + targetId + "/deactivate")
                        .with(csrf()).header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT active FROM app_users WHERE id = ?",
                Boolean.class, targetId)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_audit_log WHERE target_id = ?
                AND action IN ('USER_CREATED', 'ROLE_ASSIGNED', 'ROLE_REMOVED', 'USER_DEACTIVATED')
                """, Integer.class, targetId)).isEqualTo(4);
    }

    @Test
    void employeeCannotReadOrMutateAdminApi() throws Exception {
        TestUser employee = user(EMPLOYEE);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, employee.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, employee.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"denied_user\",\"password\":\"StrongPassword123!\",\"displayName\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutationsRequireCsrfAndCannotDeactivateLastAdmin() throws Exception {
        TestUser admin = user(ADMIN);
        mvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, admin.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"csrf_denied\",\"password\":\"StrongPassword123!\",\"displayName\":\"Denied\"}"))
                .andExpect(status().isForbidden());

        // There may be other admins in the shared test DB: use a dedicated test
        // for last-admin invariants rather than assuming this is the only one.
        mvc.perform(get("/api/admin/users/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, admin.auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCreateRequestDoesNotInsertUser() throws Exception {
        TestUser admin = user(ADMIN);
        String username = "invalid_" + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(post("/api/admin/users").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, admin.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"short\",\"displayName\":\"Test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE username = ?",
                Integer.class, username)).isZero();
    }
}
