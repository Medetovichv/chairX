package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.FundedFinanceExtension;
import kg.chairx.expense.application.ExpenseService;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.domain.*;
import kg.chairx.finance.application.DailyClosingService;
import kg.chairx.finance.api.DailyClosingRequest;
import kg.chairx.finance.domain.FinanceAccountOperationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"CHAIRX_CATALOG_PASSWORD=integration-test-password", "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@Import(PostgresTestConfiguration.class)
@ExtendWith(FundedFinanceExtension.class)
class FinancialDocumentClosingTests {
    @Autowired ExpenseService expenses;
    @Autowired DailyClosingService closings;
    @Autowired JdbcTemplate jdbc;
    LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Bishkek")); }
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("financial-document-test", null, List.of()));
    }
    @BeforeEach void setup() { authenticate(); }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM finance_daily_closing_accounts");
        jdbc.update("DELETE FROM finance_daily_closings");
        jdbc.update("DELETE FROM expenses WHERE created_by='financial-document-test'");
        SecurityContextHolder.clearContext();
    }
    CreateExpenseRequest request() {
        return new CreateExpenseRequest(ExpenseCategory.OTHER, new BigDecimal("500"), ExpensePaymentMethod.CASH, today(), null);
    }
    DailyClosingRequest closingRequest() {
        return new DailyClosingRequest(new BigDecimal("1000000"), "Пересчёт кассы", new BigDecimal("1000000"), null);
    }
    @Test void closedDayRejectsExpenseWithoutDocumentOrJournal() {
        var closing = closings.close(today(), closingRequest(), "admin");
        assertThatThrownBy(() -> expenses.create(request())).isInstanceOf(FinanceAccountOperationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE created_by='financial-document-test'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE created_by='financial-document-test'", Integer.class)).isZero();
        assertThat(closings.findByDate(today()).cash().expected()).isEqualByComparingTo(closing.cash().expected());
    }
    @Test void historicalExpenseDateRemainsLockedAfterTheNextDayBegins() {
        LocalDate yesterday = today().minusDays(1);
        UUID closingId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO finance_daily_closings(id,business_date,created_by)
                VALUES (?, ?, 'previous-day-admin')
                """, closingId, java.sql.Date.valueOf(yesterday));
        jdbc.update("""
                INSERT INTO finance_daily_closing_accounts
                    (closing_id,account_code,expected_balance,actual_balance)
                SELECT ?,code,balance,balance FROM finance_accounts
                """, closingId);

        BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class);
        long oldDocuments = jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE created_by='financial-document-test'",
                Long.class);
        long oldMovements = jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE created_by='financial-document-test'",
                Long.class);

        for (LocalDate sealed : List.of(yesterday, yesterday.minusDays(3))) {
            var rejected = new CreateExpenseRequest(UUID.randomUUID(),
                    ExpenseCategory.OTHER, new BigDecimal("500"),
                    ExpensePaymentMethod.CASH, sealed, "Late expense");
            assertThatThrownBy(() -> expenses.create(rejected))
                    .isInstanceOf(FinanceAccountOperationException.class)
                    .hasMessageContaining("закрыт");
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE created_by='financial-document-test'",
                Long.class)).isEqualTo(oldDocuments);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE created_by='financial-document-test'",
                Long.class)).isEqualTo(oldMovements);
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(before);

        // Only sealed dates are restricted; an open day stays usable.
        var accepted = expenses.create(request());
        assertThat(accepted.id()).isNotNull();
    }

    @Test void replayOfPostedExpenseIsReadOnlyAfterItsDateCloses() {
        var firstRequest = request();
        var original = expenses.create(firstRequest);
        BigDecimal cash = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class);
        BigDecimal bank = jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='BANK'",
                BigDecimal.class);
        var closing = closings.close(today(),
                new DailyClosingRequest(cash, null, bank, null), "admin");
        long documentCount = jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE id=?", Long.class, original.id());
        long movementCount = jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE' AND source_id=?",
                Long.class, original.id());

        var replay = expenses.create(firstRequest);
        assertThat(replay.id()).isEqualTo(original.id());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE id=?", Long.class, original.id()))
                .isEqualTo(documentCount);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE' AND source_id=?",
                Long.class, original.id())).isEqualTo(movementCount);
        assertThat(closings.findByDate(today()).id()).isEqualTo(closing.id());
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'",
                BigDecimal.class)).isEqualByComparingTo(cash);
    }

    @Test void expenseRacingClosingIsEitherIncludedOrFullyRejected() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Future<Boolean> expense = executor.submit(() -> {
                authenticate(); ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    expenses.create(request()); return true;
                } catch (FinanceAccountOperationException closed) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            var closing = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return closings.close(today(), closingRequest(), "admin");
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            boolean accepted = expense.get(20, TimeUnit.SECONDS);
            var result = closing.get(20, TimeUnit.SECONDS);
            BigDecimal expected = new BigDecimal(accepted ? "999500" : "1000000");
            assertThat(result.cash().expected()).isEqualByComparingTo(expected);
            assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class)).isEqualByComparingTo(expected);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE created_by='financial-document-test'", Integer.class)).isEqualTo(accepted ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE created_by='financial-document-test'", Integer.class)).isEqualTo(accepted ? 1 : 0);
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
