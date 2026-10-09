package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.application.RoleAssignmentService;
import kg.chairx.security.persistence.SecurityUserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class RoleAssignmentServiceIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID MANAGER_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    RoleAssignmentService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    SecurityUserRoleRepository userRoles;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void verifyDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");
    }

    private UUID createUser(UUID roleId, boolean active) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (
                    id, username, password_hash, display_name, active
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                "role_service_" + id.toString().replace("-", ""),
                "{noop}test-password",
                "Role Service Test",
                active
        );

        if (roleId != null) {
            jdbc.update("""
                    INSERT INTO security_user_roles (user_id, role_id)
                    VALUES (?, ?)
                    """, id, roleId);
        }

        return id;
    }

    private long auditCount(UUID actorId, UUID targetId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE actor_user_id = ?
                  AND target_type = 'USER'
                  AND target_id = ?
                  AND action = 'ROLE_ASSIGNED'
                """,
                Long.class,
                actorId,
                targetId
        );

        return count == null ? 0 : count;
    }


    @Test
    void adminCanAssignRole() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID employeeId = createUser(null, true);

        boolean assigned = service.assignRole(
                adminId, employeeId, EMPLOYEE_ROLE
        );

        assertThat(assigned).isTrue();
        assertThat(userRoles.hasRole(employeeId, EMPLOYEE_ROLE)).isTrue();
        assertThat(auditCount(adminId, employeeId)).isEqualTo(1);
    }

    @Test
    void duplicateAssignmentDoesNotCreateExtraAudit() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID employeeId = createUser(null, true);

        assertThat(service.assignRole(
                adminId, employeeId, EMPLOYEE_ROLE
        )).isTrue();

        assertThat(service.assignRole(
                adminId, employeeId, EMPLOYEE_ROLE
        )).isFalse();

        assertThat(auditCount(adminId, employeeId)).isEqualTo(1);
    }

    @Test
    void employeeCannotAssignRole() {
        UUID employeeActor = createUser(EMPLOYEE_ROLE, true);
        UUID targetId = createUser(null, true);

        assertThatThrownBy(() ->
                service.assignRole(
                        employeeActor, targetId, MANAGER_ROLE
                )
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(userRoles.hasRole(targetId, MANAGER_ROLE)).isFalse();
    }

    @Test
    void inactiveAdminCannotAssignRole() {
        UUID inactiveAdmin = createUser(ADMIN_ROLE, false);
        UUID targetId = createUser(null, true);

        assertThatThrownBy(() ->
                service.assignRole(
                        inactiveAdmin, targetId, EMPLOYEE_ROLE
                )
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(userRoles.hasRole(targetId, EMPLOYEE_ROLE)).isFalse();
    }

    @Test
    void cannotAssignRoleToInactiveUser() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID inactiveTarget = createUser(null, false);

        assertThatThrownBy(() ->
                service.assignRole(
                        adminId, inactiveTarget, EMPLOYEE_ROLE
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(userRoles.hasRole(inactiveTarget, EMPLOYEE_ROLE))
                .isFalse();
    }

    @Test
    void cannotAssignNonexistentRole() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID targetId = createUser(null, true);
        UUID nonexistentRole = UUID.randomUUID();

        assertThatThrownBy(() ->
                service.assignRole(
                        adminId, targetId, nonexistentRole
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotAssignRoleToNonexistentUser() {
        UUID adminId = createUser(ADMIN_ROLE, true);

        assertThatThrownBy(() ->
                service.assignRole(
                        adminId, UUID.randomUUID(), EMPLOYEE_ROLE
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminCanRemoveEmployeeRole() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID employeeId = createUser(EMPLOYEE_ROLE, true);

        boolean removed = service.removeRole(
                adminId, employeeId, EMPLOYEE_ROLE
        );

        assertThat(removed).isTrue();
        assertThat(userRoles.hasRole(employeeId, EMPLOYEE_ROLE)).isFalse();

        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM security_audit_log
            WHERE actor_user_id = ?
              AND target_id = ?
              AND action = 'ROLE_REMOVED'
            """,
                Long.class,
                adminId,
                employeeId
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void removingSameRoleTwiceReturnsFalse() {
        UUID adminId = createUser(ADMIN_ROLE, true);
        UUID employeeId = createUser(EMPLOYEE_ROLE, true);

        assertThat(service.removeRole(
                adminId, employeeId, EMPLOYEE_ROLE
        )).isTrue();

        assertThat(service.removeRole(
                adminId, employeeId, EMPLOYEE_ROLE
        )).isFalse();

        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM security_audit_log
            WHERE actor_user_id = ?
              AND target_id = ?
              AND action = 'ROLE_REMOVED'
            """,
                Long.class,
                adminId,
                employeeId
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void employeeCannotRemoveRole() {
        UUID employeeActor = createUser(EMPLOYEE_ROLE, true);
        UUID targetId = createUser(MANAGER_ROLE, true);

        assertThatThrownBy(() ->
                service.removeRole(
                        employeeActor, targetId, MANAGER_ROLE
                )
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(userRoles.hasRole(targetId, MANAGER_ROLE)).isTrue();
    }

    @Test
    void cannotRemoveLastActiveAdministrator() {
        UUID adminId = createUser(ADMIN_ROLE, true);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            // Временно деактивируем остальных администраторов
            // исключительно внутри тестовой транзакции.
            jdbc.update("""
                UPDATE app_users
                SET active = FALSE
                WHERE id <> ?
                  AND active = TRUE
                  AND id IN (
                      SELECT ur.user_id
                      FROM security_user_roles ur
                      JOIN security_roles r
                          ON r.id = ur.role_id
                      WHERE r.code = 'ADMIN'
                  )
                """, adminId);

            Long activeAdmins = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                JOIN security_roles r
                    ON r.id = ur.role_id
                WHERE r.code = 'ADMIN'
                  AND u.active = TRUE
                """, Long.class);

            assertThat(activeAdmins).isEqualTo(1);

            assertThatThrownBy(() ->
                    service.removeRole(adminId, adminId, ADMIN_ROLE)
            ).isInstanceOf(IllegalStateException.class);

            assertThat(userRoles.hasRole(adminId, ADMIN_ROLE)).isTrue();

            // Откатываем изменения фикстуры.
            status.setRollbackOnly();
        });
    }

    @Test
    void adminCanRemoveAdminRoleWhenAnotherAdminExists() {
        UUID firstAdmin = createUser(ADMIN_ROLE, true);
        UUID secondAdmin = createUser(ADMIN_ROLE, true);

        boolean removed = service.removeRole(
                firstAdmin, secondAdmin, ADMIN_ROLE
        );

        assertThat(removed).isTrue();
        assertThat(userRoles.hasRole(secondAdmin, ADMIN_ROLE)).isFalse();
        assertThat(userRoles.hasRole(firstAdmin, ADMIN_ROLE)).isTrue();
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() ->
                service.assignRole(null, UUID.randomUUID(), EMPLOYEE_ROLE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                service.assignRole(UUID.randomUUID(), null, EMPLOYEE_ROLE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                service.assignRole(UUID.randomUUID(), UUID.randomUUID(), null)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}