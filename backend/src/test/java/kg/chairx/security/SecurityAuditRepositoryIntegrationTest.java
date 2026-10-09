package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.security.persistence.SecurityAuditRepository;

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
class SecurityAuditRepositoryIntegrationTest {

    @Autowired
    SecurityAuditRepository audit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void verifyDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class
        )).isEqualTo("chairx_test");
    }

    @Test
    void recordsAdministrativeAction() {
        UUID targetId = UUID.randomUUID();

        UUID auditId = transactions.execute(status ->
                audit.record(
                        null,
                        "USER_CREATED",
                        "USER",
                        targetId,
                        "Created employee"
                )
        );

        assertThat(auditId).isNotNull();

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE id = ?
                  AND action = 'USER_CREATED'
                  AND target_type = 'USER'
                  AND target_id = ?
                """,
                Integer.class,
                auditId,
                targetId
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void auditIsRolledBackWithTransaction() {
        UUID targetId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            audit.record(
                    null,
                    "USER_CREATED",
                    "USER",
                    targetId,
                    "Rollback test"
            );

            status.setRollbackOnly();
        });

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_audit_log
                WHERE target_id = ?
                  AND action = 'USER_CREATED'
                """,
                Integer.class,
                targetId
        );

        assertThat(count).isZero();
    }
}