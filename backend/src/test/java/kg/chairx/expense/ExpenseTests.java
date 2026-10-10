package kg.chairx.expense;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.application.ExpenseNotFoundException;
import kg.chairx.expense.application.ExpenseService;
import kg.chairx.expense.application.ExpenseValidationException;
import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class ExpenseTests {

    @Autowired
    ExpenseService expenses;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static final LocalDate OCTOBER_1 =
            LocalDate.of(2026, 10, 1);

    private static final LocalDate NOVEMBER_1 =
            LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setup() {
        assertTestDatabase();

        jdbc.update("DELETE FROM expenses");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "expense-test-user",
                        null,
                        java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "CATALOG_ACCESS"
                                )
                        )
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        jdbc.update("DELETE FROM expenses");

        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAndPersistsExpense() {
        var created = expenses.create(
                request(
                        ExpenseCategory.ADVERTISING,
                        "5000",
                        ExpensePaymentMethod.BANK,
                        OCTOBER_1,
                        "Instagram"
                )
        );

        assertThat(created.id()).isNotNull();

        assertThat(created.amount())
                .isEqualByComparingTo("5000");

        assertThat(created.category())
                .isEqualTo(ExpenseCategory.ADVERTISING);

        assertThat(created.paymentMethod())
                .isEqualTo(ExpensePaymentMethod.BANK);

        assertThat(created.createdBy())
                .isEqualTo("expense-test-user");

        var loaded = expenses.get(created.id());

        assertThat(loaded.id()).isEqualTo(created.id());

        assertThat(loaded.comment()).isEqualTo("Instagram");
    }

    @Test
    void calculatesTotalForSelectedPeriod() {
        expenses.create(request(
                ExpenseCategory.ADVERTISING,
                "5000",
                ExpensePaymentMethod.BANK,
                OCTOBER_1,
                "Реклама"
        ));

        expenses.create(request(
                ExpenseCategory.RENT,
                "15000",
                ExpensePaymentMethod.CASH,
                LocalDate.of(2026, 10, 15),
                "Аренда"
        ));

        expenses.create(request(
                ExpenseCategory.SALARY,
                "20000",
                ExpensePaymentMethod.BANK,
                NOVEMBER_1,
                "Зарплата за ноябрь"
        ));

        assertThat(expenses.total(OCTOBER_1, NOVEMBER_1))
                .isEqualByComparingTo("20000");
    }

    @Test
    void filtersExpensesByPeriod() {
        var october = expenses.create(request(
                ExpenseCategory.RENT,
                "15000",
                ExpensePaymentMethod.CASH,
                OCTOBER_1,
                null
        ));

        expenses.create(request(
                ExpenseCategory.ADVERTISING,
                "3000",
                ExpensePaymentMethod.BANK,
                NOVEMBER_1,
                null
        ));

        var result = expenses.list(OCTOBER_1, NOVEMBER_1);

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().id())
                .isEqualTo(october.id());
    }

    @Test
    void rejectsNegativeAndFractionalAmounts() {
        assertThatThrownBy(() -> expenses.create(
                request(
                        ExpenseCategory.OTHER,
                        "-100",
                        ExpensePaymentMethod.CASH,
                        OCTOBER_1,
                        null
                )
        )).isInstanceOf(ExpenseValidationException.class);

        assertThatThrownBy(() -> expenses.create(
                request(
                        ExpenseCategory.OTHER,
                        "100.50",
                        ExpensePaymentMethod.CASH,
                        OCTOBER_1,
                        null
                )
        )).isInstanceOf(ExpenseValidationException.class);

        assertThat(expenses.list(OCTOBER_1, NOVEMBER_1))
                .isEmpty();
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() ->
                expenses.total(NOVEMBER_1, OCTOBER_1)
        ).isInstanceOf(ExpenseValidationException.class);

        assertThatThrownBy(() ->
                expenses.list(OCTOBER_1, OCTOBER_1)
        ).isInstanceOf(ExpenseValidationException.class);
    }

    @Test
    void missingExpenseReturnsNotFound() {
        assertThatThrownBy(() ->
                expenses.get(UUID.randomUUID())
        ).isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void trimsCommentAndAllowsEmptyComment() {
        var first = expenses.create(request(
                ExpenseCategory.OTHER,
                "100",
                ExpensePaymentMethod.CASH,
                OCTOBER_1,
                "  Канцелярия  "
        ));

        var second = expenses.create(request(
                ExpenseCategory.OTHER,
                "200",
                ExpensePaymentMethod.CASH,
                OCTOBER_1,
                "   "
        ));

        assertThat(expenses.get(first.id()).comment())
                .isEqualTo("Канцелярия");

        assertThat(expenses.get(second.id()).comment())
                .isNull();
    }

    @Test
    void emptyPeriodReturnsZero() {
        assertThat(expenses.total(OCTOBER_1, NOVEMBER_1))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private CreateExpenseRequest request(
            ExpenseCategory category,
            String amount,
            ExpensePaymentMethod paymentMethod,
            LocalDate date,
            String comment
    ) {
        return new CreateExpenseRequest(
                category,
                new BigDecimal(amount),
                paymentMethod,
                date,
                comment
        );
    }

    private void assertTestDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");
    }

    @Test
    void bankExpenseDebitsAccountAndCreatesJournal() {
        var expense = expenses.create(request(ExpenseCategory.OTHER, "500", ExpensePaymentMethod.BANK, OCTOBER_1, null));
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class)).isEqualByComparingTo("999500");
        assertThat(jdbc.queryForObject("SELECT amount FROM finance_movements WHERE source_type='EXPENSE' AND source_id=?", BigDecimal.class, expense.id())).isEqualByComparingTo("-500");
    }

    @Test
    void insufficientFundsRollsBackExpenseAndJournal() {
        jdbc.update("UPDATE finance_accounts SET balance=100 WHERE code='CASH'");
        assertThatThrownBy(() -> expenses.create(request(ExpenseCategory.OTHER, "500", ExpensePaymentMethod.CASH, OCTOBER_1, null)))
                .isInstanceOf(kg.chairx.finance.domain.FinanceAccountOperationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class)).isEqualByComparingTo("100");
    }


    @Test
    void sameExpenseKeyReturnsSameDocumentAndOneFinancialMovement() {
        UUID key = UUID.randomUUID();
        var command = new CreateExpenseRequest(
                key, ExpenseCategory.ADVERTISING, new BigDecimal("1500"),
                ExpensePaymentMethod.CASH, OCTOBER_1, " Instagram ");
        var first = expenses.create(command);
        var replay = expenses.create(new CreateExpenseRequest(
                key, ExpenseCategory.ADVERTISING, new BigDecimal("1500.00"),
                ExpensePaymentMethod.CASH, OCTOBER_1, "Instagram"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expenses WHERE idempotency_key=?", Long.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE' AND source_id=?",
                Long.class, first.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                .isEqualByComparingTo("998500");
    }

    @Test
    void reusedExpenseKeyWithChangedPayloadOrDelimiterCharactersConflicts() {
        UUID key = UUID.randomUUID();
        var original = expenses.create(new CreateExpenseRequest(
                key, ExpenseCategory.OTHER, new BigDecimal("500"),
                ExpensePaymentMethod.BANK, OCTOBER_1, "a|b\\nc"));

        assertThatThrownBy(() -> expenses.create(new CreateExpenseRequest(
                key, ExpenseCategory.OTHER, new BigDecimal("500"),
                ExpensePaymentMethod.CASH, OCTOBER_1, "a|b\\nc")))
                .isInstanceOfSatisfying(
                        kg.chairx.expense.application.ExpenseConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EXPENSE_IDEMPOTENCY_CONFLICT"));

        assertThatThrownBy(() -> expenses.create(new CreateExpenseRequest(
                key, ExpenseCategory.OTHER, new BigDecimal("500"),
                ExpensePaymentMethod.BANK, OCTOBER_1, "a|b\\nc|")))
                .isInstanceOf(kg.chairx.expense.application.ExpenseConflictException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_movements WHERE source_id=?", Long.class, original.id()))
                .isEqualTo(1);
    }

    @Test
    void parallelExpenseRetriesCreateOneDocumentAndOnePosting() throws Exception {
        UUID key = UUID.randomUUID();
        var command = new CreateExpenseRequest(
                key, ExpenseCategory.RENT, new BigDecimal("700"),
                ExpensePaymentMethod.CASH, OCTOBER_1, null);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<UUID> action = () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "expense-test-user", null, java.util.List.of()));
            try {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("Expense test barrier timed out");
                }
                return expenses.create(command).id();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try {
            var first = executor.submit(action);
            var second = executor.submit(action);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM expenses WHERE idempotency_key=?", Long.class, key))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_movements WHERE source_type='EXPENSE'", Long.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class))
                    .isEqualByComparingTo("999300");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void historicalExpenseWithoutKeyIsPreserved() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO expenses(id, category, amount, payment_method,
                                     expense_date, created_by, created_at)
                VALUES (?, 'OTHER', 100, 'CASH', ?, 'legacy', now())
                """, id, java.sql.Date.valueOf(OCTOBER_1));
        assertThat(expenses.get(id).amount()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject(
                "SELECT idempotency_key FROM expenses WHERE id=?",
                (rs, row) -> rs.getObject(1), id)).isNull();
    }


    @Test
    void expenseHttpRequiresKeyAndChangedPayloadReturns409() throws Exception {
        var authenticated = user("expense-api-tester");
        mvc.perform(post("/api/expenses").with(authenticated).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"OTHER","amount":200,
                                 "paymentMethod":"CASH","expenseDate":"2026-10-01"}
                                """))
                .andExpect(status().isBadRequest());

        UUID key = UUID.randomUUID();
        String first = """
                {"idempotencyKey":"%s","category":"OTHER","amount":200,
                 "paymentMethod":"CASH","expenseDate":"2026-10-01",
                 "comment":"Paper"}
                """.formatted(key);
        String changed = first.replace("\"amount\":200", "\"amount\":300");

        mvc.perform(post("/api/expenses").with(user("expense-api-tester")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/expenses").with(user("expense-api-tester")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/expenses").with(user("expense-api-tester")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "select count(*) from expenses where idempotency_key=?", Long.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='EXPENSE'", Long.class))
                .isEqualTo(1);
    }

}