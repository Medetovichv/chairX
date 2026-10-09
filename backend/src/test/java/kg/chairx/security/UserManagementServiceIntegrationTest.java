package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.application.CreateUserCommand;
import kg.chairx.security.application.UserManagementService;
import kg.chairx.security.persistence.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.dao.DataIntegrityViolationException;


import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class UserManagementServiceIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    UserManagementService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void verifyDatabase() {
        assertThat(
                jdbc.queryForObject(
                        "SELECT current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }

    /**
     * Создаёт настоящего тестового администратора.
     *
     * Это необходимо, поскольку security_audit_log.actor_user_id
     * имеет внешний ключ на app_users.id.
     */
    private UUID createTestAdmin() {
        UUID adminId = UUID.randomUUID();

        String username = "test_admin_"
                + adminId.toString().replace("-", "");

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
                username,
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

    /**
     * Создаёт уникальный username для каждого теста.
     */
    private String uniqueUsername(String prefix) {
        return prefix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Проверяет создание сотрудника,
     * хеширование пароля и запись аудита.
     */
    @Test
    void createsEmployeeWithHashedPasswordAndAudit() {
        UUID adminId = createTestAdmin();

        String username = uniqueUsername("employee");

        UUID employeeId = service.createUser(
                adminId,
                new CreateUserCommand(
                        username,
                        "StrongPassword123!",
                        "  Test Employee  "
                )
        );

        var employee = users.findById(employeeId).orElseThrow();

        // Проверяем данные сотрудника
        assertThat(employee.id()).isEqualTo(employeeId);
        assertThat(employee.username()).isEqualTo(username);
        assertThat(employee.displayName()).isEqualTo("Test Employee");
        assertThat(employee.active()).isTrue();

        // Пароль не должен храниться в открытом виде
        assertThat(employee.passwordHash())
                .isNotEqualTo("StrongPassword123!");

        assertThat(employee.passwordHash())
                .startsWith("{bcrypt}");

        var encoder = PasswordEncoderFactories
                .createDelegatingPasswordEncoder();

        assertThat(
                encoder.matches(
                        "StrongPassword123!",
                        employee.passwordHash()
                )
        ).isTrue();

        // Проверяем запись административного действия
        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND action = 'USER_CREATED'
                  AND target_type = 'USER'
                  AND target_id = ?
                """,
                Integer.class,
                adminId,
                employeeId
        );

        assertThat(auditCount).isEqualTo(1);

        // Проверяем, что пароль не попал в журнал
        String auditDetails = jdbc.queryForObject("""
                SELECT details
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND action = 'USER_CREATED'
                  AND target_id = ?
                """,
                String.class,
                adminId,
                employeeId
        );

        assertThat(auditDetails)
                .contains(username)
                .doesNotContain("StrongPassword123!")
                .doesNotContain(employee.passwordHash());
    }

    /**
     * Username должен приводиться к нижнему регистру,
     * а пробелы по краям должны удаляться.
     */
    @Test
    void normalizesUsername() {
        UUID adminId = createTestAdmin();

        String originalUsername = uniqueUsername("TEST").toUpperCase(
                Locale.ROOT
        );

        UUID employeeId = service.createUser(
                adminId,
                new CreateUserCommand(
                        "  " + originalUsername + "  ",
                        "StrongPassword123!",
                        "Employee"
                )
        );

        var employee = users.findById(employeeId).orElseThrow();

        assertThat(employee.username())
                .isEqualTo(originalUsername.toLowerCase(Locale.ROOT));

        assertThat(employee.username())
                .doesNotContain(" ");
    }

    /**
     * Имя catalog зарезервировано для системного пользователя.
     */
    @Test
    void rejectsReservedCatalogUsername() {
        UUID adminId = createTestAdmin();

        assertThatThrownBy(() ->
                service.createUser(
                        adminId,
                        new CreateUserCommand(
                                "CATALOG",
                                "StrongPassword123!",
                                "Employee"
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Слишком короткий пароль должен отклоняться.
     */
    @Test
    void rejectsWeakPassword() {
        UUID adminId = createTestAdmin();

        assertThatThrownBy(() ->
                service.createUser(
                        adminId,
                        new CreateUserCommand(
                                uniqueUsername("employee"),
                                "123",
                                "Employee"
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Username не должен содержать пробелы внутри.
     */
    @Test
    void rejectsInvalidUsername() {
        UUID adminId = createTestAdmin();

        assertThatThrownBy(() ->
                service.createUser(
                        adminId,
                        new CreateUserCommand(
                                "invalid username",
                                "StrongPassword123!",
                                "Employee"
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * PostgreSQL должен запрещать повторяющиеся username.
     */
    @Test
    void rejectsDuplicateUsername() {
        UUID adminId = createTestAdmin();

        String username = uniqueUsername("duplicate");

        CreateUserCommand command = new CreateUserCommand(
                username,
                "StrongPassword123!",
                "Employee"
        );

        // Первое создание успешно
        UUID firstEmployeeId = service.createUser(
                adminId,
                command
        );

        assertThat(firstEmployeeId).isNotNull();

        // Второе создание с таким же username запрещено
        assertThatThrownBy(() ->
                service.createUser(
                        adminId,
                        command
                )
        ).isInstanceOf(DuplicateKeyException.class);

        // Проверяем, что существует только один сотрудник
        Integer userCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                WHERE username = ?
                """,
                Integer.class,
                username
        );

        assertThat(userCount).isEqualTo(1);

        // И только одна запись аудита
        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND action = 'USER_CREATED'
                  AND target_id = ?
                """,
                Integer.class,
                adminId,
                firstEmployeeId
        );

        assertThat(auditCount).isEqualTo(1);
    }

    /**
     * Создание сотрудника без указания администратора
     * должно быть запрещено.
     */
    @Test
    void rejectsMissingActorUserId() {
        assertThatThrownBy(() ->
                service.createUser(
                        null,
                        new CreateUserCommand(
                                uniqueUsername("employee"),
                                "StrongPassword123!",
                                "Employee"
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Нельзя создавать сотрудника без обязательных данных.
     */
    @Test
    void rejectsNullCommand() {
        UUID adminId = createTestAdmin();

        assertThatThrownBy(() ->
                service.createUser(adminId, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

}