package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.api.DailyClosingRequest;
import kg.chairx.finance.application.DailyClosingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DailyClosingIntegrationTest {
    @Autowired DailyClosingService closings;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired kg.chairx.finance.persistence.FinanceAccountRepository accounts;

    @org.junit.jupiter.api.AfterEach
    void cleanupClosing() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
    }

    @Test
    void closingWithDifferenceRequiresReasonAndDoesNotChangeAccountBalance() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        // Isolate closing records from other tests, without resetting account balances.
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        BigDecimal actual = cash.add(new BigDecimal("100"));
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(actual, null, bank, null), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        var result = closings.close(today,
                new DailyClosingRequest(actual, "Излишек при пересчёте", bank, null), "admin");
        assertThat(result.cash().difference()).isEqualByComparingTo("100");
        assertThat(result.cash().note()).isEqualTo("Излишек при пересчёте");
        assertThat(closings.findByDate(today).id()).isEqualTo(result.id());
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(actual, "повтор", bank, null), "admin"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                accounts.changeBalance(kg.chairx.finance.domain.FinanceAccount.CASH, BigDecimal.ONE)))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Финансовый день уже закрыт");
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo(cash);
    }
}
