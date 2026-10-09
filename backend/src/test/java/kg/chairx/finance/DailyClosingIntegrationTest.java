package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.api.DailyClosingRequest;
import kg.chairx.finance.application.DailyClosingService;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
@AutoConfigureMockMvc
class DailyClosingIntegrationTest {
    private java.util.List<kg.chairx.finance.persistence.FinanceAccountRepository.AccountSnapshot> originalAccounts;
    private final java.util.List<java.util.UUID> testTransferIds = new java.util.ArrayList<>();

    @org.junit.jupiter.api.BeforeEach
    void snapshotAccounts() {
        originalAccounts = jdbc.query(
                "SELECT code, balance, opening_balance_initialized FROM finance_accounts ORDER BY code",
                (rs, row) -> new kg.chairx.finance.persistence.FinanceAccountRepository.AccountSnapshot(
                        kg.chairx.finance.domain.FinanceAccount.valueOf(rs.getString("code")),
                        rs.getBigDecimal("balance"),
                        rs.getBoolean("opening_balance_initialized")));
    }

    @Autowired DailyClosingService closings;
    @Autowired kg.chairx.finance.application.FinanceTransferService transferService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired kg.chairx.finance.persistence.FinanceAccountRepository accounts;

    @org.junit.jupiter.api.AfterEach
    void cleanupClosing() {
        for (java.util.UUID transferId : testTransferIds) {
            jdbc.update("DELETE FROM finance_movements WHERE source_type = 'TRANSFER' AND source_id = ?", transferId);
            jdbc.update("DELETE FROM finance_transfers WHERE id = ?", transferId);
        }
        testTransferIds.clear();
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        for (var account : originalAccounts) {
            jdbc.update("UPDATE finance_accounts SET balance = ?, opening_balance_initialized = ? WHERE code = ?",
                    account.balance(), account.initialized(), account.account().name());
        }
    }

    @Test
    void concurrentTransferAndClosingSerializeWithoutPartialMoneyMovement() throws Exception {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        jdbc.update("UPDATE finance_accounts SET balance = 100 WHERE code = 'CASH'");
        jdbc.update("UPDATE finance_accounts SET balance = 0 WHERE code = 'BANK'");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        var request = new DailyClosingRequest(
                new BigDecimal("100"), "Concurrent reconciliation",
                BigDecimal.ZERO, "Concurrent reconciliation");
        java.util.UUID transferId = java.util.UUID.randomUUID();
        testTransferIds.add(transferId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> transfer = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try {
                    transferService.transfer(transferId,
                            kg.chairx.finance.domain.FinanceAccount.CASH,
                            kg.chairx.finance.domain.FinanceAccount.BANK,
                            BigDecimal.ONE, "admin");
                    return true;
                } catch (org.springframework.dao.InvalidDataAccessApiUsageException closed) {
                    if (!closed.getMessage().contains("Финансовый день уже закрыт")) throw closed;
                    return false;
                }
            });
            Future<?> closing = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                closings.close(today, request, "admin");
                return null;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean moved = transfer.get(20, TimeUnit.SECONDS);
            closing.get(20, TimeUnit.SECONDS);
            BigDecimal cash = jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
            BigDecimal bank = jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
            assertThat(cash.add(bank)).isEqualByComparingTo("100");
            assertThat(cash).isEqualByComparingTo(moved ? "99" : "100");
            assertThat(bank).isEqualByComparingTo(moved ? "1" : "0");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_transfers WHERE id = ?", Integer.class, transferId))
                    .isEqualTo(moved ? 1 : 0);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_movements WHERE source_type='TRANSFER' AND source_id = ?",
                    Integer.class, transferId)).isEqualTo(moved ? 2 : 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closing_accounts", Integer.class))
                    .isEqualTo(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closingPreservesExpectedBalancesAndPersistsBothDiscrepancies() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        jdbc.update("UPDATE finance_accounts SET balance = 200 WHERE code = 'CASH'");
        jdbc.update("UPDATE finance_accounts SET balance = 300 WHERE code = 'BANK'");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        var closing = closings.close(today, new DailyClosingRequest(
                new BigDecimal("180"), "  Недостача при пересчёте  ",
                new BigDecimal("350"), "  Излишек по выписке  "), "admin");
        assertThat(closing.cash().expected()).isEqualByComparingTo("200");
        assertThat(closing.cash().actual()).isEqualByComparingTo("180");
        assertThat(closing.cash().difference()).isEqualByComparingTo("-20");
        assertThat(closing.cash().note()).isEqualTo("Недостача при пересчёте");
        assertThat(closing.bank().expected()).isEqualByComparingTo("300");
        assertThat(closing.bank().actual()).isEqualByComparingTo("350");
        assertThat(closing.bank().difference()).isEqualByComparingTo("50");
        assertThat(closing.bank().note()).isEqualTo("Излишек по выписке");
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class))
                .isEqualByComparingTo("300");
        assertThat(closings.closedDates()).contains(today);
        assertThat(closings.findByDate(today)).isEqualTo(closing);
    }

    @Test
    void closedDayRejectsOpeningBalanceInitializationWithoutChangingFlag() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        closings.close(today, new DailyClosingRequest(cash, null, bank, null), "admin");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = FALSE WHERE code='CASH'");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                accounts.markOpeningBalanceInitialized(kg.chairx.finance.domain.FinanceAccount.CASH)))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Финансовый день уже закрыт");
        assertThat(jdbc.queryForObject(
                "SELECT opening_balance_initialized FROM finance_accounts WHERE code='CASH'", Boolean.class))
                .isFalse();
    }

    @Test
    void previousDayClosingDoesNotBlockCurrentDayBalanceChanges() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Bishkek")).minusDays(1);
        jdbc.update("INSERT INTO finance_daily_closings (id, business_date, created_by) VALUES (?, ?, ?)",
                java.util.UUID.randomUUID(), yesterday, "historical-admin");
        BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        transactions.executeWithoutResult(status ->
                accounts.changeBalance(kg.chairx.finance.domain.FinanceAccount.CASH, BigDecimal.ONE));
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo(before.add(BigDecimal.ONE));
    }

    @Test
    void closingRejectsUninitializedAccountsWithoutPersistingAnyRecords() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = FALSE WHERE code='BANK'");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(cash, null, bank, null), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не инициализированы");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closing_accounts", Integer.class))
                .isZero();
    }

    @Test
    void closingFailureOnSecondAccountRollsBackHeaderAndFirstAccount() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(cash, null, bank.add(BigDecimal.ONE), null), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("причину");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closing_accounts", Integer.class))
                .isZero();
    }

    @Test
    void authorizedFinanceReaderCanRetrieveClosingHistoryThroughHttp() throws Exception {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        closings.close(today, new DailyClosingRequest(cash, null, bank, null), "admin");
        var reader = user("auditor").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"));
        mvc.perform(get("/api/finance/closings").with(reader))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/closings/" + today).with(reader))
                .andExpect(status().isOk());
    }

    @Test
    void transferAfterClosingRollsBackWithoutMovementsOrBalanceChanges() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        jdbc.update("UPDATE finance_accounts SET balance = 100 WHERE code = 'CASH'");
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        closings.close(today, new DailyClosingRequest(cash, null, bank, null), "admin");

        java.util.UUID transferId = java.util.UUID.randomUUID();
        assertThatThrownBy(() -> transferService.transfer(transferId,
                kg.chairx.finance.domain.FinanceAccount.CASH,
                kg.chairx.finance.domain.FinanceAccount.BANK,
                BigDecimal.ONE, "admin"))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Финансовый день уже закрыт");

        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo(cash);
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class))
                .isEqualByComparingTo(bank);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_transfers WHERE id = ?", Integer.class, transferId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type = 'TRANSFER' AND source_id = ?", Integer.class, transferId))
                .isZero();
    }

    @Test
    void authorizedEmployeeCanCloseDayThroughHttpApi() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        String body = "{\"actualCash\":" + cash.toPlainString()
                + ",\"actualBank\":" + bank.toPlainString() + "}";
        mvc.perform(post("/api/finance/closings/" + today)
                        .with(csrf())
                        .with(user("finance-manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_CLOSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closing_accounts", Integer.class))
                .isEqualTo(2);
        assertThat(closings.findByDate(today).createdBy()).isEqualTo("finance-manager");
    }

    @Test
    void closingPostRequiresAuthenticationAndFinanceClosePermission() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        String url = "/api/finance/closings/" + today;
        String body = "{\"actualCash\":0,\"actualBank\":0}";
        // CSRF is enforced before authentication for state-changing requests.
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(httpBasic("catalog", "integration-test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(csrf()).with(httpBasic("catalog", "integration-test-password"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void closingHistoryRequiresAuthenticationAndFinanceReadPermission() throws Exception {
        mvc.perform(get("/api/finance/closings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/closings")
                .with(httpBasic("catalog", "integration-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/closings/2026-01-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidDatesAndBalancesWithoutPersistingClosing() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        var valid = new DailyClosingRequest(BigDecimal.ZERO, null, BigDecimal.ZERO, null);
        assertThatThrownBy(() -> closings.close(today.minusDays(1), valid, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("только текущий день");
        assertThatThrownBy(() -> closings.close(today.plusDays(1), valid, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("только текущий день");
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(new BigDecimal("-1"), null, BigDecimal.ZERO, null), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closings.close(today,
                new DailyClosingRequest(new BigDecimal("1.5"), null, BigDecimal.ZERO, null), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closings.close(today, valid, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                .isZero();
    }

    @Test
    void concurrentClosingAllowsOnlyOneSuccessfulRequest() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized = TRUE");
        BigDecimal cash = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bishkek"));
        var request = new DailyClosingRequest(cash, null, bank, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Workers did not start");
                }
                try {
                    closings.close(today, request, "admin");
                    return true;
                } catch (IllegalStateException duplicate) {
                    if (!"День уже закрыт".equals(duplicate.getMessage())) {
                        throw duplicate;
                    }
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat((first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closings", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_daily_closing_accounts", Integer.class))
                    .isEqualTo(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
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
