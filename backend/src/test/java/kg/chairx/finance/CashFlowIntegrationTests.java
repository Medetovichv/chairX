package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.CashFlowService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class CashFlowIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CashFlowService service;

    private static final Instant OCTOBER_START =
            Instant.parse("2026-09-30T18:00:00Z");

    private static final Instant NOVEMBER_START =
            Instant.parse("2026-10-31T18:00:00Z");

    private static final Instant DECEMBER_START =
            Instant.parse("2026-11-30T18:00:00Z");

    @BeforeEach
    void setup() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");

        /*
         * Временные таблицы PostgreSQL изолируют тесты
         * от настоящих данных продаж и возвратов.
         *
         * В рамках транзакции SQL-запросы CashFlowRepository
         * обращаются к этим таблицам.
         */

        jdbc.execute("""
                CREATE TEMP TABLE payments (
                    amount NUMERIC(19,2),
                    status VARCHAR(30),
                    paid_at TIMESTAMPTZ,
                    cancelled_at TIMESTAMPTZ
                ) ON COMMIT DROP
                """);

        jdbc.execute("""
                CREATE TEMP TABLE refunds (
                    amount NUMERIC(19,2),
                    refunded_at TIMESTAMPTZ
                ) ON COMMIT DROP
                """);

        jdbc.execute("""
                CREATE TEMP TABLE exchange_settlements (
                    amount NUMERIC(19,2),
                    direction VARCHAR(20),
                    created_at TIMESTAMPTZ
                ) ON COMMIT DROP
                """);

        jdbc.execute("""
                CREATE TEMP TABLE expenses (
                    amount NUMERIC(19,2),
                    expense_date DATE
                ) ON COMMIT DROP
                """);
    }

    @Test
    void calculatesAllFinancialOperations() {

        jdbc.update("""
                INSERT INTO payments
                VALUES (200000, 'PAID',
                        '2026-10-10 12:00:00+00', NULL)
                """);

        jdbc.update("""
                INSERT INTO refunds
                VALUES (15000, '2026-10-12 12:00:00+00')
                """);

        jdbc.update("""
                INSERT INTO exchange_settlements
                VALUES (5000, 'IN', '2026-10-15 12:00:00+00')
                """);

        jdbc.update("""
                INSERT INTO exchange_settlements
                VALUES (2000, 'OUT', '2026-10-16 12:00:00+00')
                """);

        jdbc.update("""
                INSERT INTO expenses
                VALUES (30000, '2026-10-20')
                """);

        var result = service.summary(
                OCTOBER_START,
                NOVEMBER_START
        );

        assertThat(result.payments())
                .isEqualByComparingTo("200000");

        assertThat(result.refunds())
                .isEqualByComparingTo("15000");

        assertThat(result.exchangePayments())
                .isEqualByComparingTo("5000");

        assertThat(result.exchangeRefunds())
                .isEqualByComparingTo("2000");

        assertThat(result.operatingExpenses())
                .isEqualByComparingTo("30000");

        assertThat(result.totalIn())
                .isEqualByComparingTo("205000");

        assertThat(result.totalOut())
                .isEqualByComparingTo("47000");

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("158000");
    }

    @Test
    void cancelledPaymentRemainsInHistoricalPeriod() {

        jdbc.update("""
                INSERT INTO payments
                VALUES (
                    10000,
                    'CANCELLED',
                    '2026-10-10 12:00:00+00',
                    '2026-11-05 12:00:00+00'
                )
                """);

        var october = service.summary(
                OCTOBER_START,
                NOVEMBER_START
        );

        var november = service.summary(
                NOVEMBER_START,
                DECEMBER_START
        );

        assertThat(october.payments())
                .isEqualByComparingTo("10000");

        assertThat(october.paymentCorrections())
                .isEqualByComparingTo("0");

        assertThat(november.payments())
                .isEqualByComparingTo("0");

        assertThat(november.paymentCorrections())
                .isEqualByComparingTo("10000");

        assertThat(november.totalOut())
                .isEqualByComparingTo("0");

        assertThat(november.netCashFlow())
                .isEqualByComparingTo("-10000");
    }

    @Test
    void samePeriodCorrectionCancelsRegisteredPayment() {

        jdbc.update("""
                INSERT INTO payments
                VALUES (
                    25000,
                    'CANCELLED',
                    '2026-10-05 12:00:00+00',
                    '2026-10-06 12:00:00+00'
                )
                """);

        var result = service.summary(
                OCTOBER_START,
                NOVEMBER_START
        );

        assertThat(result.payments())
                .isEqualByComparingTo("25000");

        assertThat(result.paymentCorrections())
                .isEqualByComparingTo("25000");

        assertThat(result.totalOut())
                .isEqualByComparingTo("0");

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("0");
    }

    @Test
    void excludesOperationsOutsidePeriod() {

        jdbc.update("""
                INSERT INTO payments
                VALUES (1000, 'PAID',
                        '2026-09-30 17:59:59+00', NULL)
                """);

        jdbc.update("""
                INSERT INTO payments
                VALUES (2000, 'PAID',
                        '2026-09-30 18:00:00+00', NULL)
                """);

        jdbc.update("""
                INSERT INTO payments
                VALUES (3000, 'PAID',
                        '2026-10-31 18:00:00+00', NULL)
                """);

        jdbc.update("""
                INSERT INTO expenses
                VALUES (500, '2026-09-30')
                """);

        jdbc.update("""
                INSERT INTO expenses
                VALUES (700, '2026-10-01')
                """);

        jdbc.update("""
                INSERT INTO expenses
                VALUES (900, '2026-11-01')
                """);

        var result = service.summary(
                OCTOBER_START,
                NOVEMBER_START
        );

        assertThat(result.payments())
                .isEqualByComparingTo("2000");

        assertThat(result.operatingExpenses())
                .isEqualByComparingTo("700");

        assertThat(result.netCashFlow())
                .isEqualByComparingTo("1300");
    }

    @Test
    void emptyPeriodReturnsZero() {

        var result = service.summary(
                OCTOBER_START,
                NOVEMBER_START
        );

        assertThat(result.payments())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.paymentCorrections())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.refunds())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.totalIn())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.totalOut())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.netCashFlow())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}