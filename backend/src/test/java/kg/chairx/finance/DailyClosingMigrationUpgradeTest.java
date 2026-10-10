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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * P21-I upgrade contract: an existing V40 database must preserve old movement
 * registration timestamps, balances and signed closing snapshots through V45.
 * Uses a uniquely named PostgreSQL schema, never the test's application schema.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DailyClosingMigrationUpgradeTest {
    @Autowired DataSource dataSource;

    @Test
    void legacyV40DataIsPreservedAfterV45Upgrade() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        String schema = "p21_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway before = Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).target("40").load();
        Flyway after = Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).cleanDisabled(false).load();

        UUID movement = UUID.randomUUID();
        UUID closing = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        LocalDate day = LocalDate.of(2020, 10, 10);
        try {
            before.migrate();
            jdbc.update("""
                    UPDATE %s.finance_accounts
                    SET balance=25000, opening_balance_initialized=TRUE
                    WHERE code='CASH'
                    """.formatted(schema));
            jdbc.update("""
                    INSERT INTO %s.finance_movements
                      (id,account_code,amount,movement_type,source_type,
                       source_id,created_by,created_at)
                    VALUES (?, 'CASH',25000,'OPENING_BALANCE','OPENING_BALANCE',
                            ?,'legacy','2020-10-10 10:00:00+06')
                    """.formatted(schema), movement, source);
            jdbc.update("""
                    INSERT INTO %s.finance_daily_closings(id,business_date,created_by)
                    VALUES (?,?,?)
                    """.formatted(schema), closing, day, "legacy");
            jdbc.update("""
                    INSERT INTO %s.finance_daily_closing_accounts
                      (closing_id,account_code,expected_balance,actual_balance,note)
                    VALUES (?,'CASH',25000,24000,'Unexplained shortage'),
                           (?,'BANK',0,0,NULL)
                    """.formatted(schema), closing, closing);

            assertThat(after.migrate().migrationsExecuted).isGreaterThanOrEqualTo(5);
            after.validate();
            assertThat(jdbc.queryForObject("""
                    SELECT balance FROM %s.finance_accounts WHERE code='CASH'
                    """.formatted(schema), BigDecimal.class)).isEqualByComparingTo("25000");
            assertThat(jdbc.queryForObject("""
                    SELECT business_date FROM %s.finance_movements WHERE id=?
                    """.formatted(schema), (rs, row) -> rs.getDate(1).toLocalDate(), movement)).isEqualTo(day);
            assertThat(jdbc.queryForObject("""
                    SELECT business_date_source FROM %s.finance_movements WHERE id=?
                    """.formatted(schema), String.class, movement)).isEqualTo("LEGACY_INFERRED");
            assertThat(jdbc.queryForObject("""
                    SELECT (created_at AT TIME ZONE 'Asia/Bishkek')::date
                    FROM %s.finance_movements WHERE id=?
                    """.formatted(schema), LocalDate.class, movement)).isEqualTo(day);
            assertThat(jdbc.queryForObject("""
                    SELECT version FROM %s.finance_daily_closings WHERE id=?
                    """.formatted(schema), Long.class, closing)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT expected_balance FROM %s.finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """.formatted(schema), BigDecimal.class, closing)).isEqualByComparingTo("25000");
            assertThat(jdbc.queryForObject("""
                    SELECT difference FROM %s.finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """.formatted(schema), BigDecimal.class, closing)).isEqualByComparingTo("-1000");

            // New revision is stored separately. No backfill is allowed to
            // rewrite the original expected/actual/difference from V40.
            jdbc.update("""
                    INSERT INTO %s.finance_daily_closing_adjustments
                      (closing_id,account_code,expected_balance,adjusted_by)
                    VALUES (?,'CASH',24000,'p21-test')
                    """.formatted(schema), closing);
            assertThat(jdbc.queryForObject("""
                    SELECT expected_balance FROM %s.finance_daily_closing_adjustments
                    WHERE closing_id=? AND account_code='CASH'
                    """.formatted(schema), BigDecimal.class, closing)).isEqualByComparingTo("24000");
            assertThat(jdbc.queryForObject("""
                    SELECT expected_balance FROM %s.finance_daily_closing_accounts
                    WHERE closing_id=? AND account_code='CASH'
                    """.formatted(schema), BigDecimal.class, closing)).isEqualByComparingTo("25000");

            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM %s.security_permissions
                    WHERE code LIKE 'DAILY_CLOSING_%%'
                    """.formatted(schema), Long.class)).isEqualTo(6);
            assertThat(after.migrate().migrationsExecuted).isZero();
        } finally {
            after.clean();
        }
    }
}
