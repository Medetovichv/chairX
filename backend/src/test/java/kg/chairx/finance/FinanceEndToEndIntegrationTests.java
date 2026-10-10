package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.application.ExpenseService;
import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;
import kg.chairx.finance.application.CashFlowService;
import kg.chairx.finance.application.FinanceQueryService;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentStatus;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real services + real PostgreSQL journal, without fake finance movements.
 * Balances and generated movements are restored by FundedFinanceExtension.
 * We compare reporting deltas so that any legitimate historical fixture
 * movements already in the test database cannot skew this scenario.
 */
@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class FinanceEndToEndIntegrationTests {
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired SaleService sales;
    @Autowired PaymentService payments;
    @Autowired ExpenseService expenses;
    @Autowired CashFlowService cashFlow;
    @Autowired FinanceQueryService accounts;

    private UUID warehouse;
    private UUID product;
    private UUID variant;
    private UUID customer;
    private UUID expenseId;

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("chairx_test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "finance-e2e-test", null, List.of()));

        clearInventoryAndSales();
        jdbc.update("""
                UPDATE finance_accounts SET balance=100000,
                    opening_balance_initialized=true WHERE code='CASH'
                """);
        jdbc.update("""
                UPDATE finance_accounts SET balance=200000,
                    opening_balance_initialized=true WHERE code='BANK'
                """);

        warehouse = jdbc.queryForObject(
                "select id from warehouses where code='HOME'", UUID.class);
        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        customer = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,name,active,created_at,updated_at)
                values (?, 'Finance E2E product', true, now(), now())
                """, product);
        jdbc.update("""
                insert into product_variants(
                    id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values (?, ?, 'Black', 8500, true, now(), now())
                """, variant, product);
        jdbc.update("""
                insert into customers(
                    id,full_name,phone,active,created_at,updated_at)
                values (?, 'Finance E2E customer', '+996555123456', true, now(), now())
                """, customer);
        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(), warehouse, variant, 10,
                new BigDecimal("50000"), "finance-e2e");
    }

    @AfterEach
    void cleanup() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("chairx_test");
        if (expenseId != null) jdbc.update("delete from expenses where id=?", expenseId);
        clearInventoryAndSales();
        jdbc.update("delete from customers where id=?", customer);
        jdbc.update("delete from product_variants where id=?", variant);
        jdbc.update("delete from products where id=?", product);
        SecurityContextHolder.clearContext();
    }

    @Test
    void paymentExpenseReversalAndIdempotentRetriesMatchLedgerAndCashFlow() {
        LocalDate today = LocalDate.now(BISHKEK);
        Instant from = today.atStartOfDay(BISHKEK).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(BISHKEK).toInstant();
        var baseline = cashFlow.summary(from, to);

        var sale = sales.create(new CreateSaleRequest(
                UUID.randomUUID(), customer, FulfillmentType.SELF_PICKUP,
                List.of(new CreateSaleItemRequest(
                        variant, warehouse, 1, new BigDecimal("8500")))));

        var payment = payments.create(new CreatePaymentRequest(
                sale.id(), PaymentMethod.CASH, null, "E2E payment"));
        assertThat(payment.amount()).isEqualByComparingTo("8500");
        assertThat(balance("CASH")).isEqualByComparingTo("108500");

        UUID key = UUID.randomUUID();
        var expenseRequest = new CreateExpenseRequest(
                key, ExpenseCategory.OTHER, new BigDecimal("1000"),
                ExpensePaymentMethod.CASH, today, "E2E operating expense");
        var expense = expenses.create(expenseRequest);
        expenseId = expense.id();
        assertThat(expense.amount()).isEqualByComparingTo("1000");
        assertThat(balance("CASH")).isEqualByComparingTo("107500");

        var reversed = payments.cancel(
                payment.id(), new CancelPaymentRequest("Отмена оплаты"));
        assertThat(reversed.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance("CASH")).isEqualByComparingTo("99000");

        assertThat(payments.cancel(payment.id(), new CancelPaymentRequest("Повтор")))
                .isEqualTo(reversed);
        assertThat(expenses.create(expenseRequest).id()).isEqualTo(expense.id());

        assertThat(balance("CASH")).isEqualByComparingTo("99000");
        assertThat(balance("BANK")).isEqualByComparingTo("200000");
        assertThat(accounts.getAccounts().stream()
                .filter(a -> a.code().equals("CASH")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("99000");

        assertPosting("PAYMENT", payment.id(), "SALE_PAYMENT", "8500");
        assertPosting("PAYMENT_REVERSAL", payment.id(), "PAYMENT_REVERSAL", "-8500");
        assertPosting("EXPENSE", expense.id(), "EXPENSE", "-1000");

        var after = cashFlow.summary(from, to);
        assertThat(after.payments().subtract(baseline.payments()))
                .isEqualByComparingTo("8500");
        assertThat(after.paymentCorrections().subtract(baseline.paymentCorrections()))
                .isEqualByComparingTo("8500");
        assertThat(after.operatingExpenses().subtract(baseline.operatingExpenses()))
                .isEqualByComparingTo("1000");
        assertThat(after.totalIn().subtract(baseline.totalIn()))
                .isEqualByComparingTo("8500");
        assertThat(after.totalOut().subtract(baseline.totalOut()))
                .isEqualByComparingTo("9500");
        assertThat(after.netCashFlow().subtract(baseline.netCashFlow()))
                .isEqualByComparingTo("-1000");

        BigDecimal actualSignedJournal = jdbc.queryForObject("""
                SELECT SUM(amount)
                FROM finance_movements
                WHERE (source_type='PAYMENT' AND source_id=?)
                   OR (source_type='PAYMENT_REVERSAL' AND source_id=?)
                   OR (source_type='EXPENSE' AND source_id=?)
                """, BigDecimal.class, payment.id(), payment.id(), expense.id());
        assertThat(actualSignedJournal).isEqualByComparingTo("-1000");
        assertThat(balance("CASH")).isEqualByComparingTo(
                new BigDecimal("100000").add(actualSignedJournal));
    }

    private void assertPosting(
            String sourceType, UUID sourceId, String movementType, String amount) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM finance_movements
                WHERE source_type=? AND source_id=?
                    AND movement_type=? AND account_code='CASH'
                """, Long.class, sourceType, sourceId, movementType)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT amount FROM finance_movements
                WHERE source_type=? AND source_id=?
                    AND movement_type=? AND account_code='CASH'
                """, BigDecimal.class, sourceType, sourceId, movementType))
                .isEqualByComparingTo(amount);
    }

    private BigDecimal balance(String code) {
        return jdbc.queryForObject(
                "select balance from finance_accounts where code=?", BigDecimal.class, code);
    }

    private void clearInventoryAndSales() {
        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_transfer_cost_origins, inventory_transfers,
                    inventory_cost_movements, inventory_cost_allocations,
                    inventory_cost_restorations, inventory_cost_write_offs,
                    inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items, returns, payments, deliveries, sale_items, sales,
                    stock_movements, inventory_balances
                RESTART IDENTITY
                """);
        jdbc.update("""
                DELETE FROM audit_entries
                WHERE entity_type IN ('PAYMENT','SALE','STOCK_MOVEMENT')
                """);
    }
}
