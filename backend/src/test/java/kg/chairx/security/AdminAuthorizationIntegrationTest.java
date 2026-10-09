package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AdminAuthorizationIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID MANAGER_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void verifyDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");
    }

    private String createUser(UUID roleId) {
        UUID id = UUID.randomUUID();
        String username = "admin_auth_" + id;

        var encoder = PasswordEncoderFactories
                .createDelegatingPasswordEncoder();

        jdbc.update("""
                INSERT INTO app_users
                    (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, TRUE)
                """,
                id,
                username,
                encoder.encode("test-password"),
                "Admin Authorization Test"
        );

        jdbc.update("""
                INSERT INTO security_user_roles (user_id, role_id)
                VALUES (?, ?)
                """, id, roleId);

        return username;
    }

    private static String basic(String username, String password) {
        String credentials = username + ":" + password;

        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void unauthenticatedUserCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic("catalog", "integration-test-password")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotAccessAdmin() throws Exception {
        String username = createUser(EMPLOYEE_ROLE);

        mvc.perform(get("/api/admin/users")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotAccessAdmin() throws Exception {
        String username = createUser(MANAGER_ROLE);

        mvc.perform(get("/api/admin/users")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPassesAuthorization() throws Exception {
        String username = createUser(ADMIN_ROLE);

        mvc.perform(get("/api/admin/users")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isNotFound());
    }
}