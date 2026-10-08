package kg.chairx.refund;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.refund.api.CreateRefundRequest;
import kg.chairx.refund.application.RefundRuleViolationException;
import kg.chairx.refund.application.RefundService;
import kg.chairx.refund.domain.RefundMethod;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.domain.ReturnCondition;
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
import java.util.List;
import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class RefundCompensationTests {

    @Autowired
    RefundService refunds;

    @Autowired
    PaymentService payments;

    @Autowired
    ReturnService returns;

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID home;
    UUID product;
    UUID variant;
    UUID customer;

    @BeforeEach
    void fixture() {
        authenticate();
        assertTestDatabase();
        clearData();

        home = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code = 'HOME'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products (
                    id, name, active, created_at, updated_at
                )
                VALUES (?, 'Compensation test', true, now(), now())
                """, product);

        jdbc.update("""
                INSERT INTO product_variants (
                    id, product_id, name,
                    recommended_sale_price, active,
                    created_at, updated_at
                )
                VALUES (?, ?, 'Black', 8500, true, now(), now())
                """, variant, product);

        jdbc.update("""
                INSERT INTO customers (
                    id, full_name, phone,
                    active, created_at, updated_at
                )
                VALUES (
                    ?, 'Compensation Customer',
                    '+996555000099',
                    true, now(), now()
                )
                """, customer);

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        20,
                        "COMPENSATION_TEST",
                        UUID.randomUUID(),
                        "compensation-test"
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();
        clearData();

        jdbc.update(
                "DELETE FROM customers WHERE id = ?",
                customer
        );

        jdbc.update(
                "DELETE FROM product_variants WHERE id = ?",
                variant
        );

        jdbc.update(
                "DELETE FROM products WHERE id = ?",
                product
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void sameReturnCannotBeRefundedTwice() {
        var sale = createPaidAndFulfilledSale();

        var saleReturn = createReturn(
                sale.id(),
                sale.items().getFirst().id()
        );

        refunds.create(refundRequest(
                sale.id(),
                saleReturn.id(),
                "3000"
        ));

        assertThatThrownBy(() -> refunds.create(
                refundRequest(
                        sale.id(),
                        saleReturn.id(),
                        "2000"
                )
        )).isInstanceOfSatisfying(
                RefundRuleViolationException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo("RETURN_ALREADY_REFUNDED")
        );

        assertThat(refundCount(sale.id())).isEqualTo(1);
    }

    @Test
    void differentReturnsCanBeRefundedSeparately() {
        var sale = createPaidAndFulfilledSale();

        var firstReturn = createReturn(
                sale.id(),
                sale.items().getFirst().id()
        );

        var secondReturn = createReturn(
                sale.id(),
                sale.items().getFirst().id()
        );

        refunds.create(refundRequest(
                sale.id(),
                firstReturn.id(),
                "3000"
        ));

        refunds.create(refundRequest(
                sale.id(),
                secondReturn.id(),
                "3000"
        ));

        assertThat(refundCount(sale.id())).isEqualTo(2);
    }

    private kg.chairx.sale.api.SaleResponse
    createPaidAndFulfilledSale() {
        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        2,
                                        new BigDecimal("8500")
                                )
                        )
                )
        );

        payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        sales.fulfill(sale.id());
        return sale;
    }

    private kg.chairx.returning.domain.Return createReturn(
            UUID saleId,
            UUID saleItemId
    ) {
        return returns.create(
                new CreateReturnRequest(
                        saleId,
                        home,
                        UUID.randomUUID(),
                        List.of(
                                new CreateReturnItemRequest(
                                        saleItemId,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        ),
                        "Возврат товара",
                        null
                )
        );
    }

    private CreateRefundRequest refundRequest(
            UUID saleId,
            UUID returnId,
            String amount
    ) {
        return new CreateRefundRequest(
                saleId,
                returnId,
                new BigDecimal(amount),
                RefundMethod.CASH,
                "Возврат денег",
                null,
                null,
                UUID.randomUUID()
        );
    }

    private long refundCount(UUID saleId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE sale_id = ?",
                Long.class,
                saleId
        );
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "compensation-test-user",
                        null,
                        List.of()
                )
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items, returns, payments,
                    deliveries, sale_items, sales,
                    stock_movements, inventory_balances
                RESTART IDENTITY
                """);

        jdbc.execute(
                "ALTER SEQUENCE sale_number_seq RESTART WITH 1"
        );

        jdbc.update("""
                DELETE FROM audit_entries
                WHERE entity_type IN (
                    'REFUND', 'RETURN', 'PAYMENT',
                    'DELIVERY', 'SALE', 'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");
    }
}