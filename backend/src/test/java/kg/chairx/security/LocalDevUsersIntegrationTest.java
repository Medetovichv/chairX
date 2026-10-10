package kg.chairx.security;

import kg.chairx.security.application.AdminBootstrapService;
import kg.chairx.security.application.LocalDevUsersService;
import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=local-dev-integration-catalog",
        "CHAIRX_DEV_SEED_USERS=false"
})
class LocalDevUsersIntegrationTest {
    private static final String MANAGER_PASSWORD = "ManagerLocalPassword123!";
    private static final String EMPLOYEE_PASSWORD = "EmployeeLocalPassword123!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("chairx_local_dev_test")
            .withUsername("chairx")
            .withPassword("chairx_test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AdminBootstrapService bootstrap;

    @Autowired
    LocalDevUsersService seeder;

    @Autowired
    AppUserRepository users;

    @Autowired
    SecurityRoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_local_dev_test");
        jdbc.update("DELETE FROM security_audit_log");
        jdbc.update("DELETE FROM security_user_roles");
        jdbc.update("DELETE FROM app_users");
        jdbc.update("UPDATE security_bootstrap_state SET initialized = FALSE, initialized_at = NULL WHERE id = 1");
    }

    private void createAdmin() {
        assertThat(bootstrap.initialize("admin", "AdminBootstrapPassword123!", "Administrator")).isTrue();
    }

    @Test
    void firstStartCreatesHashedUsersWithDistinctRoles() {
        createAdmin();

        seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD);

        var manager = users.findByUsername("manager").orElseThrow();
        var employee = users.findByUsername("employee").orElseThrow();

        assertThat(manager.active()).isTrue();
        assertThat(employee.active()).isTrue();
        assertThat(roles.findRoleCodesByUserId(manager.id())).isEqualTo(Set.of("MANAGER"));
        assertThat(roles.findRoleCodesByUserId(employee.id())).isEqualTo(Set.of("EMPLOYEE"));
        assertThat(roles.findRoleCodesByUserId(users.findByUsername("admin").orElseThrow().id()))
                .contains("ADMIN");

        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        assertThat(manager.passwordHash()).startsWith("{bcrypt}");
        assertThat(employee.passwordHash()).startsWith("{bcrypt}");
        assertThat(encoder.matches(MANAGER_PASSWORD, manager.passwordHash())).isTrue();
        assertThat(encoder.matches(EMPLOYEE_PASSWORD, employee.passwordHash())).isTrue();
        assertThat(manager.passwordHash()).isNotEqualTo(employee.passwordHash());
    }

    @Test
    void restartingDoesNotOverwriteIdsPasswordsOrRoles() {
        createAdmin();
        seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD);

        var originalManager = users.findByUsername("manager").orElseThrow();
        var originalEmployee = users.findByUsername("employee").orElseThrow();

        seeder.ensureUsers("admin", "ChangedManagerPassword456!", "ChangedEmployeePassword456!");

        assertThat(users.findByUsername("manager").orElseThrow()).isEqualTo(originalManager);
        assertThat(users.findByUsername("employee").orElseThrow()).isEqualTo(originalEmployee);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_user_roles", Integer.class)).isEqualTo(3);
    }

    @Test
    void invalidPasswordOrMissingAdminDoesNotCreateUsers() {
        createAdmin();
        assertThatThrownBy(() -> seeder.ensureUsers("admin", MANAGER_PASSWORD, "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12-128")
                .hasMessageNotContaining(MANAGER_PASSWORD);

        assertThat(users.findByUsername("manager")).isEmpty();
        assertThat(users.findByUsername("employee")).isEmpty();
    }

    @Test
    void existingConflictingManagerIsNotChangedAndNewEmployeeIsNotCreated() {
        createAdmin();
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, 'manager', '{noop}existing', 'Existing manager', TRUE)
                """, userId);

        assertThatThrownBy(() -> seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected status/roles");

        assertThat(users.findByUsername("manager").orElseThrow().passwordHash()).isEqualTo("{noop}existing");
        assertThat(users.findByUsername("employee")).isEmpty();
    }

    @Test
    void partialSeedIsRolledBackWhenExistingEmployeeConflicts() {
        createAdmin();
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, 'employee', '{noop}existing', 'Existing employee', FALSE)
                """, userId);

        assertThatThrownBy(() -> seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD))
                .isInstanceOf(IllegalStateException.class);

        assertThat(users.findByUsername("manager")).isEmpty();
        assertThat(users.findByUsername("employee").orElseThrow().active()).isFalse();
    }
    @Test
    void previouslyInitializedDatabaseWithDifferentlyNamedAdminReusesExistingAccount() {
        // A real local machine may already have an ADMIN named 'owner', not 'admin'.
        assertThat(bootstrap.initialize("owner", "OriginalOwnerPassword123!", "Owner"))
                .isTrue();
        var originalOwner = users.findByUsername("owner").orElseThrow();

        // Existing bootstrap is a no-op on the next startup.
        assertThat(bootstrap.initialize("admin", "NewPasswordNotApplied123!", "New Admin"))
                .isFalse();

        seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD);

        assertThat(users.findByUsername("admin")).isEmpty();
        assertThat(users.findByUsername("owner").orElseThrow()).isEqualTo(originalOwner);
        assertThat(roles.findRoleCodesByUserId(originalOwner.id())).contains("ADMIN");
        assertThat(roles.findRoleCodesByUserId(users.findByUsername("manager").orElseThrow().id()))
                .isEqualTo(Set.of("MANAGER"));
        assertThat(roles.findRoleCodesByUserId(users.findByUsername("employee").orElseThrow().id()))
                .isEqualTo(Set.of("EMPLOYEE"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM security_audit_log
                WHERE actor_user_id = ? AND action IN ('USER_CREATED', 'ROLE_ASSIGNED')
                """, Integer.class, originalOwner.id())).isEqualTo(4);

        seeder.ensureUsers("admin", "AnotherManagerPassword123!", "AnotherEmployeePassword123!");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(3);
    }

    @Test
    void noActiveAdminCannotSeedIntoExistingDatabaseAndDoesNotCreateBackdoor() {
        assertThatThrownBy(() ->
                seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active ADMIN");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_user_roles", Integer.class)).isZero();
    }

    @Test
    void configuredNonAdminIsNotSilentlyReplacedByAnotherAdministrator() {
        assertThat(bootstrap.initialize("owner", "OriginalOwnerPassword123!", "Owner")).isTrue();
        UUID conflictingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, display_name, active)
                VALUES (?, 'admin', '{noop}existing', 'Non-admin account', TRUE)
                """, conflictingId);

        assertThatThrownBy(() ->
                seeder.ensureUsers("admin", MANAGER_PASSWORD, EMPLOYEE_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an active ADMIN");

        assertThat(users.findByUsername("admin").orElseThrow().id()).isEqualTo(conflictingId);
        assertThat(users.findByUsername("manager")).isEmpty();
        assertThat(users.findByUsername("employee")).isEmpty();
    }


}

