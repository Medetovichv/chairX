package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class EmployeeAuthenticationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String username;

    private static String basic(String username, String password) {
        String credentials = username + ":" + password;

        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );
    }

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");

        UUID id = UUID.randomUUID();
        username = "employee_auth_" + id;

        var encoder = PasswordEncoderFactories
                .createDelegatingPasswordEncoder();

        jdbc.update("""
                INSERT INTO app_users
                    (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, TRUE)
                """,
                id,
                username,
                encoder.encode("secure-test-password"),
                "Authentication Test Employee"
        );
    }

    @Test
    void activeEmployeeCanAuthenticate() throws Exception {
        mvc.perform(get("/api/products")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "secure-test-password")
                        ))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/products")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "wrong-password")
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveEmployeeIsRejected() throws Exception {
        jdbc.update("""
                UPDATE app_users
                SET active = FALSE
                WHERE username = ?
                """, username);

        mvc.perform(get("/api/products")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic(username, "secure-test-password")
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogAuthenticationStillWorks() throws Exception {
        mvc.perform(get("/api/products")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic("catalog", "integration-test-password")
                        ))
                .andExpect(status().isOk());
    }
}