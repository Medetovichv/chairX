package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CurrentUserApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String secret = "F02-Strong-Secret-123!";

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String create(String role, boolean active) {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        String username = "f02_" + UUID.randomUUID().toString().replace("-", "");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, ?)
                """, id, username,
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(secret),
                "F02 Example", active);
        if (role != null) {
            jdbc.update("""
                    INSERT INTO security_user_roles(user_id, role_id)
                    SELECT ?, id FROM security_roles WHERE code = ?
                    """, id, role);
        }
        return username;
    }

    @Test
    void rolesAndPermissionsReflectRealPostgresAssignments() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER", "EMPLOYEE")) {
            String username = create(role, true);
            var payload = jdbc.queryForMap("""
                    SELECT id, username, display_name FROM app_users WHERE username = ?
                    """, username);
            List<String> granted = jdbc.queryForList("""
                    SELECT DISTINCT p.permission_code
                    FROM security_user_roles ur JOIN security_role_permissions p ON p.role_id = ur.role_id
                    WHERE ur.user_id = ? ORDER BY p.permission_code
                    """, String.class, payload.get("id"));

            var result = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,basic(username,secret)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(payload.get("id").toString()))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.displayName").value("F02 Example"))
                    .andExpect(jsonPath("$.roles[0]").value(role))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andReturn();
            var body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain(secret, "password_hash", "CATALOG_ACCESS");
            var mapper = new tools.jackson.databind.json.JsonMapper();
            var node = mapper.readTree(body);
            assertThat(node.get("permissions").size()).isEqualTo(granted.size());
            for (int i=0;i<granted.size();i++) {
                assertThat(node.get("permissions").get(i).asText()).isEqualTo(granted.get(i));
            }
        }
    }

    @Test
    void badPasswordNoCredentialsAndDisabledUserReturn401() throws Exception {
        String username = create("EMPLOYEE",true);
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, basic(username,"incorrect")))
                .andExpect(status().isUnauthorized());
        String disabled = create("EMPLOYEE",false);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,basic(disabled,secret)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void technicalCatalogAndRolelessAccountCannotSeeStaffProfile() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,
                basic("catalog","integration-test-password"))).andExpect(status().isForbidden());
        String roleless = create(null,true);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,basic(roleless,secret)))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingCsrfAndFailClosedRoutingRemainEnabled() throws Exception {
        String username = create("EMPLOYEE",true);
        mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION,basic(username,secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isString())
                .andExpect(jsonPath("$.token").isString());
        mvc.perform(get("/api/auth/unknown").header(HttpHeaders.AUTHORIZATION,basic(username,secret)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/me").header(HttpHeaders.AUTHORIZATION,basic(username,secret)))
                .andExpect(status().isForbidden());
    }
}
