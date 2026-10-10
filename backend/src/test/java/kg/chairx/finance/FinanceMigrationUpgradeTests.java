package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"CHAIRX_CATALOG_PASSWORD=integration-test-password", "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@Import(PostgresTestConfiguration.class)
class FinanceMigrationUpgradeTests {
    @Autowired DataSource dataSource;
    @Test void upgradesAppliedV33WithoutChangingBalancesOrHistoricalJournal() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        String schema = "audit_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway before = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("33").load();
        Flyway after = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).cleanDisabled(false).load();
        try {
            before.migrate();
            jdbc.update("UPDATE " + schema + ".finance_accounts SET balance=100, opening_balance_initialized=TRUE WHERE code='CASH'");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".finance_movements(id,account_code,amount,movement_type,source_type,source_id,created_by) VALUES (?, 'CASH', 100, 'SALE_PAYMENT', 'PAYMENT', ?, 'audit')", id, id);
            UUID legacyExpense = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema
                    + ".expenses(id, category, amount, payment_method, expense_date, created_by, created_at)"
                    + " VALUES (?, 'OTHER', 150, 'CASH', CURRENT_DATE, 'legacy', now())", legacyExpense);

            // Upgrade from V33 through V34, V35 and V36 without touching
            // the existing financial balance, journal or legacy expense.
            assertThat(after.migrate().migrationsExecuted).isGreaterThanOrEqualTo(3);
            after.validate();
            assertThat(jdbc.queryForObject("SELECT balance FROM " + schema + ".finance_accounts WHERE code='CASH'", BigDecimal.class)).isEqualByComparingTo("100");
            assertThat(jdbc.queryForObject("SELECT amount FROM " + schema + ".finance_movements WHERE id=?", BigDecimal.class, id)).isEqualByComparingTo("100");
            assertThat(jdbc.queryForObject("SELECT idempotency_key FROM " + schema
                    + ".expenses WHERE id=?", (rs, row) -> rs.getObject(1), legacyExpense)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".finance_document_posting_issues WHERE document_id=?",
                    Long.class, legacyExpense)).isEqualTo(1);
            jdbc.update("INSERT INTO " + schema + ".finance_movements(id,account_code,amount,movement_type,source_type,source_id,created_by) VALUES (?, 'CASH', -100, 'PAYMENT_REVERSAL', 'PAYMENT_REVERSAL', ?, 'audit')", UUID.randomUUID(), id);
            assertThat(after.migrate().migrationsExecuted).isZero();
        } finally { after.clean(); }
    }
}
