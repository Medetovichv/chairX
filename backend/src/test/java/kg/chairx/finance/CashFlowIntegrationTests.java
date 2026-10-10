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
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class CashFlowIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CashFlowService service;

    private static final Instant OCTOBER_START = Instant.parse("2026-09-30T18:00:00Z");
    private static final Instant NOVEMBER_START = Instant.parse("2026-10-31T18:00:00Z");
    private static final Instant DECEMBER_START = Instant.parse("2026-11-30T18:00:00Z");

    @BeforeEach
    void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");

        // A session-local journal lets these tests verify journal semantics
        // without altering historical financial data in the integration DB.
        jdbc.execute("""
                CREATE TEMP TABLE finance_movements (
                    amount NUMERIC(19,2),
                    movement_type VARCHAR(30),
                    source_type VARCHAR(30),
                    created_at TIMESTAMPTZ
                ) ON COMMIT DROP
                """);
    }

    void post(String type, String source, String amount, String when) {
        jdbc.update("""
                INSERT INTO finance_movements(amount, movement_type, source_type, created_at)
                VALUES (?, ?, ?, ?)
                """, new BigDecimal(amount), type, source, Timestamp.from(Instant.parse(when)));
    }

    @Test
    void calculatesAllFinancialOperationsFromJournal() {
        post("SALE_PAYMENT", "PAYMENT", "200000", "2026-10-10T12:00:00Z");
        post("CUSTOMER_REFUND", "REFUND", "-15000", "2026-10-12T12:00:00Z");
        post("SALE_PAYMENT", "EXCHANGE_SETTLEMENT", "5000", "2026-10-15T12:00:00Z");
        post("CUSTOMER_REFUND", "EXCHANGE_SETTLEMENT", "-2000", "2026-10-16T12:00:00Z");
        post("EXPENSE", "EXPENSE", "-30000", "2026-10-20T12:00:00Z");

        var result = service.summary(OCTOBER_START, NOVEMBER_START);
        assertThat(result.payments()).isEqualByComparingTo("200000");
        assertThat(result.refunds()).isEqualByComparingTo("15000");
        assertThat(result.exchangePayments()).isEqualByComparingTo("5000");
        assertThat(result.exchangeRefunds()).isEqualByComparingTo("2000");
        assertThat(result.operatingExpenses()).isEqualByComparingTo("30000");
        assertThat(result.totalIn()).isEqualByComparingTo("205000");
        assertThat(result.totalOut()).isEqualByComparingTo("47000");
        assertThat(result.netCashFlow()).isEqualByComparingTo("158000");
    }

    @Test
    void cancelledPaymentRemainsInHistoricalPeriod() {
        post("SALE_PAYMENT", "PAYMENT", "10000", "2026-10-10T12:00:00Z");
        post("PAYMENT_REVERSAL", "PAYMENT_REVERSAL", "-10000", "2026-11-05T12:00:00Z");
        var october = service.summary(OCTOBER_START, NOVEMBER_START);
        var november = service.summary(NOVEMBER_START, DECEMBER_START);
        assertThat(october.payments()).isEqualByComparingTo("10000");
        assertThat(october.paymentCorrections()).isEqualByComparingTo("0");
        assertThat(november.payments()).isEqualByComparingTo("0");
        assertThat(november.paymentCorrections()).isEqualByComparingTo("10000");
        assertThat(november.totalOut()).isEqualByComparingTo("0");
        assertThat(november.netCashFlow()).isEqualByComparingTo("-10000");
    }

    @Test
    void samePeriodCorrectionOffsetsRegisteredPayment() {
        post("SALE_PAYMENT", "PAYMENT", "25000", "2026-10-05T12:00:00Z");
        post("PAYMENT_REVERSAL", "PAYMENT_REVERSAL", "-25000", "2026-10-06T12:00:00Z");
        var result = service.summary(OCTOBER_START, NOVEMBER_START);
        assertThat(result.payments()).isEqualByComparingTo("25000");
        assertThat(result.paymentCorrections()).isEqualByComparingTo("25000");
        assertThat(result.totalOut()).isEqualByComparingTo("0");
        assertThat(result.netCashFlow()).isEqualByComparingTo("0");
    }

    @Test
    void postingAtBishkekDayBoundaryIsIncludedExactlyOnce() {
        post("SALE_PAYMENT", "PAYMENT", "1000", "2026-09-30T17:59:59Z");
        post("SALE_PAYMENT", "PAYMENT", "2000", "2026-09-30T18:00:00Z");
        post("SALE_PAYMENT", "PAYMENT", "3000", "2026-10-31T18:00:00Z");
        post("EXPENSE", "EXPENSE", "-700", "2026-10-09T18:00:00Z");

        var october = service.summary(OCTOBER_START, NOVEMBER_START);
        var november = service.summary(NOVEMBER_START, DECEMBER_START);
        assertThat(october.payments()).isEqualByComparingTo("2000");
        assertThat(october.operatingExpenses()).isEqualByComparingTo("700");
        assertThat(october.netCashFlow()).isEqualByComparingTo("1300");
        assertThat(november.payments()).isEqualByComparingTo("3000");
    }

    @Test
    void openingBalancesAndInternalTransfersDoNotCountAsRevenueOrCosts() {
        post("OPENING_BALANCE", "OPENING_BALANCE", "100000", "2026-10-10T12:00:00Z");
        post("TRANSFER", "TRANSFER", "-5000", "2026-10-11T12:00:00Z");
        post("TRANSFER", "TRANSFER", "5000", "2026-10-11T12:00:00Z");
        var result = service.summary(OCTOBER_START, NOVEMBER_START);
        assertThat(result.totalIn()).isEqualByComparingTo("0");
        assertThat(result.totalOut()).isEqualByComparingTo("0");
        assertThat(result.netCashFlow()).isEqualByComparingTo("0");
    }

    @Test
    void emptyPeriodReturnsZero() {
        var result = service.summary(OCTOBER_START, NOVEMBER_START);
        assertThat(result.payments()).isEqualByComparingTo("0");
        assertThat(result.paymentCorrections()).isEqualByComparingTo("0");
        assertThat(result.refunds()).isEqualByComparingTo("0");
        assertThat(result.totalIn()).isEqualByComparingTo("0");
        assertThat(result.totalOut()).isEqualByComparingTo("0");
        assertThat(result.netCashFlow()).isEqualByComparingTo("0");
    }
}
