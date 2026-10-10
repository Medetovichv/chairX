package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.application.ExpenseService;
import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;
import kg.chairx.finance.application.FinanceReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@Transactional
@WithMockUser(username = "reconciliation-tester")
class FinanceReconciliationIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceReconciliationService diagnostics;
    @Autowired ExpenseService expenses;

    @Test
    void findsUnpostedHistoricalExpenseButDoesNotCreateMovements() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("chairx_test");
        UUID legacy = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO expenses(
                    id, category, amount, payment_method,
                    expense_date, created_by, created_at
                ) VALUES (?, 'OTHER', 300, 'CASH', ?, 'legacy', now())
                """, legacy, java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)));

        var posted = expenses.create(new CreateExpenseRequest(
                UUID.randomUUID(), ExpenseCategory.OTHER, new BigDecimal("200"),
                ExpensePaymentMethod.BANK, LocalDate.of(2026, 10, 1), "Correct posting"));
        long movementsBefore = jdbc.queryForObject(
                "select count(*) from finance_movements", Long.class);
        BigDecimal cashBefore = jdbc.queryForObject(
                "select balance from finance_accounts where code='CASH'", BigDecimal.class);

        var issues = diagnostics.listIssues();
        var missing = issues.stream().filter(i -> i.documentId().equals(legacy))
                .findFirst().orElseThrow();
        assertThat(missing.documentSource()).isEqualTo("EXPENSE");
        assertThat(missing.problem()).isEqualTo("MISSING");
        assertThat(missing.expectedAmount()).isEqualByComparingTo("-300");
        assertThat(missing.postedAmount()).isEqualByComparingTo("0");
        assertThat(issues.stream().noneMatch(i -> i.documentId().equals(posted.id())))
                .isTrue();

        assertThat(jdbc.queryForObject("select count(*) from finance_movements", Long.class))
                .isEqualTo(movementsBefore);
        assertThat(jdbc.queryForObject(
                "select balance from finance_accounts where code='CASH'", BigDecimal.class))
                .isEqualByComparingTo(cashBefore);
    }

    @Test
    void mismatchDetectionRemainsReadOnly() {
        UUID legacy = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO expenses(
                    id, category, amount, payment_method,
                    expense_date, created_by, created_at
                ) VALUES (?, 'OTHER', 300, 'CASH', ?, 'legacy', now())
                """, legacy, java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)));
        jdbc.update("""
                INSERT INTO finance_movements(
                    id, account_code, amount, movement_type,
                    source_type, source_id, created_by, created_at
                ) VALUES (?, 'CASH', -200, 'EXPENSE', 'EXPENSE', ?, 'legacy', now())
                """, UUID.randomUUID(), legacy);
        var issue = diagnostics.listIssues().stream()
                .filter(i -> i.documentId().equals(legacy)).findFirst().orElseThrow();
        assertThat(issue.problem()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(issue.expectedAmount()).isEqualByComparingTo("-300");
        assertThat(issue.postedAmount()).isEqualByComparingTo("-200");
    }
}
