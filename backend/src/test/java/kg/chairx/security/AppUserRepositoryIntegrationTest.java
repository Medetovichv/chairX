package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.persistence.AppUserRepository;

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
class AppUserRepositoryIntegrationTest {

    @Autowired
    AppUserRepository users;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    private UUID userId;
    private String username;

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");

        userId = UUID.randomUUID();
        username = "appuser_test_" + userId;

        jdbc.update("""
                INSERT INTO app_users
                    (id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, ?, ?)
                """,
                userId,
                username,
                "{noop}test-password",
                "Test Employee",
                true
        );
    }

    @Test
    void createsActiveUser() {
        UUID newUserId = UUID.randomUUID();
        String newUsername = "created_test_" + newUserId;

        transactions.executeWithoutResult(status ->
                users.create(
                        newUserId,
                        newUsername,
                        "{bcrypt}test-hash",
                        "New Employee"
                )
        );

        var result = users.findById(newUserId);

        assertThat(result).isPresent();

        var employee = result.orElseThrow();

        assertThat(employee.id()).isEqualTo(newUserId);
        assertThat(employee.username()).isEqualTo(newUsername);
        assertThat(employee.displayName()).isEqualTo("New Employee");
        assertThat(employee.passwordHash()).isEqualTo("{bcrypt}test-hash");
        assertThat(employee.active()).isTrue();
    }

    @Test
    void findsUserByUsername() {
        var result = users.findByUsername(username);

        assertThat(result).isPresent();

        var user = result.orElseThrow();

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.username()).isEqualTo(username);
        assertThat(user.displayName()).isEqualTo("Test Employee");
        assertThat(user.passwordHash()).isEqualTo("{noop}test-password");
        assertThat(user.active()).isTrue();
    }

    @Test
    void findsUserById() {
        var result = users.findById(userId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().username())
                .isEqualTo(username);
    }

    @Test
    void returnsEmptyForUnknownUser() {
        assertThat(users.findByUsername("missing_" + UUID.randomUUID()))
                .isEmpty();

        assertThat(users.findById(UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void returnsEmptyForInvalidArguments() {
        assertThat(users.findByUsername(null)).isEmpty();
        assertThat(users.findByUsername("   ")).isEmpty();
        assertThat(users.findById(null)).isEmpty();
    }

    @Test
    void returnsInactiveUserWithCorrectStatus() {
        jdbc.update("""
                UPDATE app_users
                SET active = FALSE
                WHERE id = ?
                """, userId);

        var result = users.findByUsername(username);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().active()).isFalse();
    }
}