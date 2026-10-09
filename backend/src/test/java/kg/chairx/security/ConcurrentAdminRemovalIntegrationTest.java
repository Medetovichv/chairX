package kg.chairx.security;

import kg.chairx.security.application.RoleAssignmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import kg.chairx.security.application.UserManagementService;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password"
})
@Testcontainers
class ConcurrentAdminRemovalIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("chairx_concurrency_test")
                    .withUsername("chairx")
                    .withPassword("chairx_test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RoleAssignmentService service;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    JdbcTemplate jdbc;

    private UUID createAdmin() {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (
                    id, username, password_hash, display_name, active
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
                id,
                "concurrent_admin_" + id.toString().replace("-", ""),
                "{noop}test-password",
                "Concurrent Admin"
        );

        jdbc.update("""
                INSERT INTO security_user_roles (user_id, role_id)
                VALUES (?, ?)
                """,
                id,
                ADMIN_ROLE
        );

        return id;
    }

    private long countActiveAdmins() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                JOIN security_roles r
                    ON r.id = ur.role_id
                WHERE r.code = 'ADMIN'
                  AND u.active = TRUE
                """, Long.class);

        return count == null ? 0 : count;
    }

    @BeforeEach
    void resetTestUsers() {
        jdbc.update("""
            DELETE FROM security_audit_log
            WHERE actor_user_id IN (
                SELECT id FROM app_users
            )
            """);

        jdbc.update("""
            DELETE FROM security_user_roles
            """);

        jdbc.update("""
            DELETE FROM app_users
            """);
    }

    @Test
    void concurrentRoleRemovalAndDeactivationPreservesAdmin() throws Exception {
        UUID firstAdmin = createAdmin();
        UUID secondAdmin = createAdmin();

        assertThat(countActiveAdmins()).isEqualTo(2);

        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            var removeRole = executor.submit(() -> {
                start.await();

                try {
                    return service.removeRole(
                            firstAdmin,
                            secondAdmin,
                            ADMIN_ROLE
                    );
                } catch (IllegalStateException
                         | org.springframework.security.access.AccessDeniedException ex) {
                    return false;
                }
            });

            var deactivate = executor.submit(() -> {
                start.await();

                try {
                    return userManagementService.deactivateUser(
                            secondAdmin,
                            firstAdmin
                    );
                } catch (IllegalStateException
                         | org.springframework.security.access.AccessDeniedException ex) {
                    return false;
                }
            });

            start.countDown();

            boolean removed = removeRole.get(15, TimeUnit.SECONDS);
            boolean deactivated = deactivate.get(15, TimeUnit.SECONDS);

            assertThat(removed ^ deactivated).isTrue();

            assertThat(countActiveAdmins()).isEqualTo(1);
        }
    }

    @Test
    void concurrentRemovalPreservesLastAdministrator() throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_concurrency_test");

        assertThat(countActiveAdmins()).isZero();

        UUID firstAdmin = createAdmin();
        UUID secondAdmin = createAdmin();

        assertThat(countActiveAdmins()).isEqualTo(2);

        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            var first = executor.submit(() -> {
                start.await();

                try {
                    return service.removeRole(
                            firstAdmin,
                            secondAdmin,
                            ADMIN_ROLE
                    );
                } catch (IllegalStateException
                         | org.springframework.security.access.AccessDeniedException ex) {
                    return false;
                }
            });

            var second = executor.submit(() -> {
                start.await();

                try {
                    return service.removeRole(
                            secondAdmin,
                            firstAdmin,
                            ADMIN_ROLE
                    );
                } catch (IllegalStateException
                         | org.springframework.security.access.AccessDeniedException ex) {
                    return false;
                }
            });

            start.countDown();

            boolean firstResult = first.get(15, TimeUnit.SECONDS);
            boolean secondResult = second.get(15, TimeUnit.SECONDS);

            // Только одно снятие роли должно быть успешным.
            assertThat(firstResult ^ secondResult).isTrue();

            // Один активный администратор обязательно остаётся.
            assertThat(countActiveAdmins()).isEqualTo(1);
        }
    }
}