package kg.chairx.security;

import kg.chairx.security.application.AdminBootstrapService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password"
})
class AdminBootstrapServiceIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("chairx_bootstrap_test")
                    .withUsername("chairx")
                    .withPassword("chairx_test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    AdminBootstrapService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetBootstrapState() {

        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_bootstrap_test");

        jdbc.update("""
        INSERT INTO security_roles (
            id,
            code,
            name,
            system_role
        )
        VALUES (?, 'ADMIN', 'Administrator', TRUE)
        ON CONFLICT (id) DO NOTHING
        """,
                ADMIN_ROLE
        );
        jdbc.update("DELETE FROM security_audit_log");
        jdbc.update("DELETE FROM security_user_roles");
        jdbc.update("DELETE FROM app_users");

        jdbc.update("""
                UPDATE security_bootstrap_state
                SET initialized = FALSE,
                    initialized_at = NULL
                WHERE id = 1
                """);
    }

    @Test
    void concurrentBootstrapCreatesOnlyOneAdministrator() throws Exception {

        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            var first = executor.submit(() -> {
                start.await();

                return service.initialize(
                        "concurrent_admin_1",
                        "StrongBootstrapPassword123!",
                        "First Administrator"
                );
            });

            var second = executor.submit(() -> {
                start.await();

                return service.initialize(
                        "concurrent_admin_2",
                        "StrongBootstrapPassword456!",
                        "Second Administrator"
                );
            });

            // Оба потока начинают одновременно.
            start.countDown();

            boolean firstResult = first.get(30, TimeUnit.SECONDS);
            boolean secondResult = second.get(30, TimeUnit.SECONDS);

            // Ровно один поток должен создать администратора.
            assertThat(firstResult).isNotEqualTo(secondResult);

            assertThat(countUsers()).isEqualTo(1);
            assertThat(isInitialized()).isTrue();

            Integer adminCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                WHERE ur.role_id = ?
                  AND u.active = TRUE
                """,
                    Integer.class,
                    ADMIN_ROLE
            );

            assertThat(adminCount).isEqualTo(1);
        }
    }

    @Test
    void rollsBackBootstrapWhenAdminRoleAssignmentFails() {

        String username = uniqueUsername();

        // Временно удаляем роль ADMIN из тестовой базы.
        // Назначение роли должно завершиться ошибкой внешнего ключа.
        jdbc.update("""
            DELETE FROM security_role_permissions
            WHERE role_id = ?
            """, ADMIN_ROLE);

        jdbc.update("""
            DELETE FROM security_roles
            WHERE id = ?
            """, ADMIN_ROLE);

        assertThatThrownBy(() ->
                service.initialize(
                        username,
                        "StrongBootstrapPassword123!",
                        "Rollback Administrator"
                )
        ).isInstanceOf(
                org.springframework.dao.DataIntegrityViolationException.class
        );

        // Пользователь не должен сохраниться.
        Integer userCount = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM app_users
            WHERE username = ?
            """,
                Integer.class,
                username
        );

        assertThat(userCount).isZero();

        // Bootstrap не должен считаться завершённым.
        assertThat(isInitialized()).isFalse();
    }

    @Test
    void createsFirstAdministrator() {

        String username = uniqueUsername();

        boolean created = service.initialize(
                username,
                "StrongBootstrapPassword123!",
                "ChairX Administrator"
        );

        assertThat(created).isTrue();

        var result = jdbc.queryForMap("""
                SELECT
                    u.id,
                    u.password_hash,
                    u.active,
                    ur.role_id
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                WHERE u.username = ?
                """,
                username
        );

        String passwordHash =
                (String) result.get("password_hash");

        assertThat(passwordHash).startsWith("{bcrypt}");

        assertThat(
                PasswordEncoderFactories
                        .createDelegatingPasswordEncoder()
                        .matches(
                                "StrongBootstrapPassword123!",
                                passwordHash
                        )
        ).isTrue();

        assertThat(result.get("active")).isEqualTo(true);
        assertThat(result.get("role_id")).isEqualTo(ADMIN_ROLE);

        assertThat(isInitialized()).isTrue();
    }

    @Test
    void secondBootstrapDoesNotCreateAnotherAdministrator() {

        assertThat(service.initialize(
                "first_admin",
                "StrongBootstrapPassword123!",
                "First Administrator"
        )).isTrue();

        assertThat(service.initialize(
                "second_admin",
                "AnotherStrongPassword123!",
                "Second Administrator"
        )).isFalse();

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
        assertThat(isInitialized()).isTrue();
    }

    @Test
    void rejectsWeakPassword() {

        assertThatThrownBy(() ->
                service.initialize(
                        "admin",
                        "123",
                        "Administrator"
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(isInitialized()).isFalse();
        assertThat(countUsers()).isZero();
    }

    @Test
    void rejectsMissingConfiguration() {

        assertThatThrownBy(() ->
                service.initialize(
                        null,
                        null,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(isInitialized()).isFalse();
        assertThat(countUsers()).isZero();
    }

    @Test
    void refusesBootstrapWhenUsersAlreadyExist() {

        UUID existingUserId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (
                    id,
                    username,
                    password_hash,
                    display_name,
                    active
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
                existingUserId,
                uniqueUsername(),
                "{noop}test-password",
                "Existing Employee"
        );

        assertThatThrownBy(() ->
                service.initialize(
                        "new_admin",
                        "StrongBootstrapPassword123!",
                        "New Administrator"
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(isInitialized()).isFalse();
        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void existingAdministratorMarksBootstrapInitialized() {

        UUID adminId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (
                    id,
                    username,
                    password_hash,
                    display_name,
                    active
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
                adminId,
                uniqueUsername(),
                "{noop}test-password",
                "Existing Administrator"
        );

        jdbc.update("""
                INSERT INTO security_user_roles (
                    user_id,
                    role_id
                )
                VALUES (?, ?)
                """,
                adminId,
                ADMIN_ROLE
        );

        boolean created = service.initialize(
                "another_admin",
                "StrongBootstrapPassword123!",
                "Another Administrator"
        );

        assertThat(created).isFalse();
        assertThat(countUsers()).isEqualTo(1);
        assertThat(isInitialized()).isTrue();
    }

    private boolean isInitialized() {

        return Boolean.TRUE.equals(
                jdbc.queryForObject("""
                        SELECT initialized
                        FROM security_bootstrap_state
                        WHERE id = 1
                        """,
                        Boolean.class
                )
        );
    }

    private int countUsers() {

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                """,
                Integer.class
        );

        return count == null ? 0 : count;
    }

    private String uniqueUsername() {

        return "bootstrap_"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);
    }
}