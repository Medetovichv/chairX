package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.application.CreateUserCommand;
import kg.chairx.security.application.UserManagementService;
import kg.chairx.security.persistence.SecurityAuditRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class UserCreationRollbackIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    UserManagementService service;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    SecurityAuditRepository auditRepository;

    @Test
    void rollsBackUserCreationWhenAuditFails() {

        // Проверяем, что работаем с тестовой БД.
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");

        UUID adminId = createTestAdmin();
        String username = uniqueUsername("rollback");

        // Имитируем ошибку записи аудита.
        doThrow(new DataIntegrityViolationException(
                "Simulated audit failure"
        )).when(auditRepository).record(
                eq(adminId),
                eq("USER_CREATED"),
                eq("USER"),
                any(UUID.class),
                anyString()
        );

        // Создание сотрудника должно завершиться ошибкой.
        assertThatThrownBy(() ->
                service.createUser(
                        adminId,
                        new CreateUserCommand(
                                username,
                                "StrongPassword123!",
                                "Rollback Employee"
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        // Сотрудник не должен сохраниться.
        Integer userCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                WHERE username = ?
                """,
                Integer.class,
                username
        );

        assertThat(userCount).isZero();

        // Аудит создания тоже не должен сохраниться.
        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND action = 'USER_CREATED'
                """,
                Integer.class,
                adminId
        );

        assertThat(auditCount).isZero();

        // Убеждаемся, что ошибка произошла именно при записи аудита.
        verify(auditRepository).record(
                eq(adminId),
                eq("USER_CREATED"),
                eq("USER"),
                any(UUID.class),
                anyString()
        );
    }

    private UUID createTestAdmin() {

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
                uniqueUsername("admin"),
                "{noop}test-password",
                "Test Administrator"
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

        return adminId;
    }

    private String uniqueUsername(String prefix) {
        return prefix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}