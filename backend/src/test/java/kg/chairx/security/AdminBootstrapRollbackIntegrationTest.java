package kg.chairx.security;

import kg.chairx.security.application.AdminBootstrapService;
import kg.chairx.security.application.AdminBootstrapRunner;
import kg.chairx.security.persistence.SecurityUserRoleRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password"
})
class AdminBootstrapRollbackIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("chairx_bootstrap_rollback_test")
                    .withUsername("chairx")
                    .withPassword("chairx_test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AdminBootstrapService service;

    @Autowired
    JdbcTemplate jdbc;

    // Runner must not execute bootstrap while its dependency is mocked.
    // This test invokes AdminBootstrapService.initialize() explicitly.
    @MockitoBean
    AdminBootstrapRunner bootstrapRunner;

    @MockitoBean
    SecurityUserRoleRepository userRoles;

    @Test
    void rollsBackUserCreationWhenRoleAssignmentFails() {

        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_bootstrap_rollback_test");

        String username = "rollback_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        when(userRoles.assignRole(any(UUID.class), any(UUID.class)))
                .thenThrow(new IllegalStateException(
                        "Simulated ADMIN role assignment failure"
                ));

        assertThatThrownBy(() ->
                service.initialize(
                        username,
                        "StrongBootstrapPassword123!",
                        "Rollback Administrator"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated ADMIN role assignment failure");

        Integer userCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                WHERE username = ?
                """,
                Integer.class,
                username
        );

        assertThat(userCount).isZero();

        Integer roleAssignmentCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_user_roles
                """,
                Integer.class
        );

        assertThat(roleAssignmentCount).isZero();

        Boolean initialized = jdbc.queryForObject("""
                SELECT initialized
                FROM security_bootstrap_state
                WHERE id = 1
                """,
                Boolean.class
        );

        assertThat(initialized).isFalse();
    }
}