package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.application.UserManagementService;
import kg.chairx.security.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class UserDeactivationIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    UserManagementService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private UUID createUser(UUID roleId) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (
                    id, username, password_hash, display_name, active
                )
                VALUES (?, ?, ?, ?, TRUE)
                """,
                id,
                "deactivation_" + id.toString().replace("-", ""),
                "{noop}test-password",
                "Test Employee"
        );

        jdbc.update("""
                INSERT INTO security_user_roles (user_id, role_id)
                VALUES (?, ?)
                """, id, roleId);

        return id;
    }

    private long auditCount(UUID actorId, UUID targetId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND target_id = ?
                  AND action = 'USER_DEACTIVATED'
                """, Long.class, actorId, targetId);

        return count == null ? 0 : count;
    }

    @Test
    void adminCanDeactivateEmployee() {
        UUID adminId = createUser(ADMIN_ROLE);
        UUID employeeId = createUser(EMPLOYEE_ROLE);

        boolean result = service.deactivateUser(adminId, employeeId);

        assertThat(result).isTrue();
        assertThat(users.findById(employeeId).orElseThrow().active())
                .isFalse();
        assertThat(auditCount(adminId, employeeId)).isEqualTo(1);
    }

    @Test
    void repeatedDeactivationDoesNotCreateExtraAudit() {
        UUID adminId = createUser(ADMIN_ROLE);
        UUID employeeId = createUser(EMPLOYEE_ROLE);

        assertThat(service.deactivateUser(adminId, employeeId))
                .isTrue();

        assertThat(service.deactivateUser(adminId, employeeId))
                .isFalse();

        assertThat(auditCount(adminId, employeeId)).isEqualTo(1);
    }

    @Test
    void employeeCannotDeactivateAnotherEmployee() {
        UUID actorId = createUser(EMPLOYEE_ROLE);
        UUID targetId = createUser(EMPLOYEE_ROLE);

        assertThatThrownBy(() ->
                service.deactivateUser(actorId, targetId)
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(users.findById(targetId).orElseThrow().active())
                .isTrue();

        assertThat(auditCount(actorId, targetId)).isZero();
    }

    @Test
    void inactiveAdminCannotDeactivateEmployee() {
        UUID adminId = createUser(ADMIN_ROLE);
        UUID employeeId = createUser(EMPLOYEE_ROLE);

        jdbc.update("""
                UPDATE app_users
                SET active = FALSE
                WHERE id = ?
                """, adminId);

        assertThatThrownBy(() ->
                service.deactivateUser(adminId, employeeId)
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(users.findById(employeeId).orElseThrow().active())
                .isTrue();
    }

    @Test
    void rejectsNullArguments() {
        UUID adminId = createUser(ADMIN_ROLE);

        assertThatThrownBy(() ->
                service.deactivateUser(null, adminId)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                service.deactivateUser(adminId, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotDeactivateNonexistentUser() {
        UUID adminId = createUser(ADMIN_ROLE);

        assertThatThrownBy(() ->
                service.deactivateUser(adminId, UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}