package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.persistence.SecurityUserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class SecurityUserRoleRepositoryIntegrationTest {

    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    SecurityUserRoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void verifyDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");
    }

    private UUID createTestUser() {
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
                "role_test_" + id.toString().replace("-", ""),
                "{noop}test-password",
                "Role Test Employee"
        );

        return id;
    }

    @Test
    void assignsRoleToUser() {
        UUID userId = createTestUser();

        Boolean assigned = transactions.execute(status ->
                roles.assignRole(userId, EMPLOYEE_ROLE)
        );

        assertThat(assigned).isTrue();
        assertThat(roles.hasRole(userId, EMPLOYEE_ROLE)).isTrue();
    }

    @Test
    void assigningSameRoleTwiceDoesNotCreateDuplicate() {
        UUID userId = createTestUser();

        transactions.executeWithoutResult(status -> {
            assertThat(roles.assignRole(userId, EMPLOYEE_ROLE)).isTrue();
            assertThat(roles.assignRole(userId, EMPLOYEE_ROLE)).isFalse();
        });

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_user_roles
                WHERE user_id = ?
                  AND role_id = ?
                """,
                Integer.class,
                userId,
                EMPLOYEE_ROLE
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void removesAssignedRole() {
        UUID userId = createTestUser();

        transactions.executeWithoutResult(status ->
                roles.assignRole(userId, EMPLOYEE_ROLE)
        );

        Boolean removed = transactions.execute(status ->
                roles.removeRole(userId, EMPLOYEE_ROLE)
        );

        assertThat(removed).isTrue();
        assertThat(roles.hasRole(userId, EMPLOYEE_ROLE)).isFalse();
    }

    @Test
    void removingMissingRoleReturnsFalse() {
        UUID userId = createTestUser();

        Boolean removed = transactions.execute(status ->
                roles.removeRole(userId, EMPLOYEE_ROLE)
        );

        assertThat(removed).isFalse();
    }

    @Test
    void roleAssignmentRollsBackWithTransaction() {
        UUID userId = createTestUser();

        transactions.executeWithoutResult(status -> {
            roles.assignRole(userId, EMPLOYEE_ROLE);
            status.setRollbackOnly();
        });

        assertThat(roles.hasRole(userId, EMPLOYEE_ROLE)).isFalse();
    }
}