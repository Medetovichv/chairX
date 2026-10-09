package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.persistence.SecurityRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class SecurityRoleRepositoryIntegrationTest {

    private static final UUID ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID MANAGER_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    SecurityRoleRepository roles;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");

        // Удаляем только данные тестовых пользователей.
        // Системные роли и разрешения из V29 сохраняем.
        jdbc.update("""
                DELETE FROM security_user_roles
                WHERE user_id IN (
                    SELECT id FROM app_users
                    WHERE username LIKE 'rbac_test_%'
                )
                """);

        jdbc.update("""
                DELETE FROM app_users
                WHERE username LIKE 'rbac_test_%'
                """);

        userId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users
                    (id, username, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                "rbac_test_" + userId,
                "{noop}test-password",
                "RBAC Test User"
        );
    }

    private UUID createUserWithRole(UUID roleId) {
        UUID id = UUID.randomUUID();

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
                id,
                "permission_test_" + id.toString().replace("-", ""),
                "{noop}test-password",
                "Permission Test User"
        );

        jdbc.update("""
            INSERT INTO security_user_roles (
                user_id,
                role_id
            )
            VALUES (?, ?)
            """,
                id,
                roleId
        );

        return id;
    }

    private void assignRole(UUID roleId) {
        jdbc.update("""
                INSERT INTO security_user_roles (user_id, role_id)
                VALUES (?, ?)
                """, userId, roleId);
    }

    @Test
    void userWithoutRolesHasNoPermissions() {
        assertThat(roles.findRoleCodesByUserId(userId)).isEmpty();
        assertThat(roles.findPermissionsByUserId(userId)).isEmpty();
    }

    @Test
    void roleAssignmentLockWorksInsideTransaction() {
        transactions.executeWithoutResult(status -> {
            roles.lockRoleAssignments();

            assertThat(roles.countActiveAdministrators())
                    .isGreaterThanOrEqualTo(0);
        });
    }
    @Test
    void adminHasRoleAssignmentPermission() {
        UUID adminId = createUserWithRole(
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );

        assertThat(
                roles.userHasPermission(adminId, "ROLES_ASSIGN")
        ).isTrue();
    }

    @Test
    void employeeCannotAssignRoles() {
        UUID employeeId = createUserWithRole(
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );

        assertThat(
                roles.userHasPermission(employeeId, "ROLES_ASSIGN")
        ).isFalse();
    }

    @Test
    void inactiveAdminCannotAssignRoles() {
        UUID adminId = createUserWithRole(
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );

        jdbc.update(
                "UPDATE app_users SET active = FALSE WHERE id = ?",
                adminId
        );

        assertThat(
                roles.userHasPermission(adminId, "ROLES_ASSIGN")
        ).isFalse();
    }

    @Test
    void employeeReceivesAssignedRolePermissions() {
        assignRole(EMPLOYEE_ROLE);

        assertThat(roles.findRoleCodesByUserId(userId))
                .containsExactly("EMPLOYEE");

        assertThat(roles.findPermissionsByUserId(userId))
                .containsExactlyInAnyOrder(
                        "SALES_READ",
                        "SALES_CREATE",
                        "INVENTORY_READ"
                );
    }

    @Test
    void multipleRolesCombinePermissionsWithoutDuplicates() {
        assignRole(EMPLOYEE_ROLE);
        assignRole(MANAGER_ROLE);

        assertThat(roles.findRoleCodesByUserId(userId))
                .containsExactlyInAnyOrder("EMPLOYEE", "MANAGER");

        assertThat(roles.findPermissionsByUserId(userId))
                .containsExactlyInAnyOrder(
                        "FINANCE_READ",
                        "SALES_READ",
                        "SALES_CREATE",
                        "SALES_UPDATE",
                        "INVENTORY_READ",
                        "INVENTORY_RECEIVE",
                        "INVENTORY_TRANSFER",
                        "PURCHASE_READ",
                        "PURCHASE_CREATE"
                );
    }

    @Test
    void existingRoleIsFoundById() {
        UUID employeeRoleId =
                UUID.fromString("00000000-0000-0000-0000-000000000003");

        assertThat(roles.existsById(employeeRoleId)).isTrue();
    }

    @Test
    void nonexistentRoleIsNotFoundById() {
        assertThat(roles.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void adminHasAllSeededPermissions() {
        assignRole(ADMIN_ROLE);

        var allPermissions = jdbc.queryForList(
                "SELECT code FROM security_permissions",
                String.class
        );

        assertThat(roles.findRoleCodesByUserId(userId))
                .containsExactly("ADMIN");

        assertThat(roles.findPermissionsByUserId(userId))
                .containsExactlyInAnyOrderElementsOf(allPermissions);
    }
}