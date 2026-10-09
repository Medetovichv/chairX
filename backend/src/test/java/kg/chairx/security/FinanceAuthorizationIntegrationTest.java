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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
class FinanceAuthorizationIntegrationTest {

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

    private String createUser(UUID roleId) {
        UUID id = UUID.randomUUID();
        String username = "finance_auth_" + id;

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
                "Finance Authorization Test"
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

    @BeforeEach
    void verifyDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");
    }

    @Test
    void unauthenticatedUserCannotAccessFinance() throws Exception {
        mvc.perform(get("/api/finance/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogCannotAccessFinance() throws Exception {
        mvc.perform(get("/api/finance/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic("catalog", "integration-test-password")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotAccessFinance() throws Exception {
        String username = createUser(EMPLOYEE_ROLE);

        mvc.perform(get("/api/finance/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerReceivesCorrectFinanceAccounts() throws Exception {
        String username = createUser(MANAGER_ROLE);

        mvc.perform(get("/api/finance/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CASH"))
                .andExpect(jsonPath("$[0].balance").isNumber())
                .andExpect(jsonPath("$[0].initialized").isBoolean())
                .andExpect(jsonPath("$[1].code").value("BANK"))
                .andExpect(jsonPath("$[1].balance").isNumber())
                .andExpect(jsonPath("$[1].initialized").isBoolean())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void managerPassesFinanceAuthorization() throws Exception {
        String username = createUser(MANAGER_ROLE);

        mvc.perform(get("/api/finance/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isOk());
    }

    @Test
    void adminPassesFinanceAuthorization() throws Exception {
        String username = createUser(ADMIN_ROLE);

        mvc.perform(get("/api/finance/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "test-password")
                        ))
                .andExpect(status().isOk());
    }
}