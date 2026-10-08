package kg.chairx.exchange;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.application.ExchangeService;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.refund.api.CreateRefundRequest;
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
import kg.chairx.exchange.application.ExchangeRuleViolationException;

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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class ExchangeCreationTests {

    @Autowired ExchangeService exchanges;
    @Autowired SaleService sales;
    @Autowired PaymentService payments;
    @Autowired ReturnService returns;
    @Autowired RefundService refunds;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;

    UUID warehouseId;
    UUID productId;
    UUID variantId;
    UUID customerId;

    @BeforeEach
    void setup() {
        authenticate();
        assertTestDatabase();
        clean();

        warehouseId = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code = 'HOME'",
                UUID.class
        );

        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products
                    (id, name, active, created_at, updated_at)
                VALUES (?, 'Exchange creation test', true, now(), now())
                """, productId);

        jdbc.update("""
                INSERT INTO product_variants
                    (id, product_id, name,
                     recommended_sale_price,
                     active, created_at, updated_at)
                VALUES (?, ?, 'Black', 8500, true, now(), now())
                """, variantId, productId);

        jdbc.update("""
                INSERT INTO customers
                    (id, full_name, phone,
                     active, created_at, updated_at)
                VALUES (?, 'Exchange Customer',
                        '+996555222222', true, now(), now())
                """, customerId);

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouseId,
                        variantId,
                        ADJUSTMENT_IN,
                        30,
                        "EXCHANGE_CREATION_TEST",
                        UUID.randomUUID(),
                        "exchange-test"
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();
        clean();

        jdbc.update(
                "DELETE FROM customers WHERE id = ?",
                customerId
        );

        jdbc.update(
                "DELETE FROM product_variants WHERE id = ?",
                variantId
        );

        jdbc.update(
                "DELETE FROM products WHERE id = ?",
                productId
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void moreExpensiveProductRequiresAdditionalPayment() {
        UUID returnId = createPaidSaleAndReturn("8500");

        var exchange = exchanges.create(
                request(returnId, "10000", UUID.randomUUID())
        );

        assertThat(exchange.returnedValue())
                .isEqualByComparingTo("8500");

        assertThat(exchange.newSaleTotal())
                .isEqualByComparingTo("10000");

        assertThat(exchange.additionalPaymentDue())
                .isEqualByComparingTo("1500");

        assertThat(exchange.refundDue())
                .isEqualByComparingTo("0");

        assertThat(exchange.status())
                .isEqualTo(ExchangeStatus.PENDING_SETTLEMENT);

        assertThat(sales.get(exchange.newSaleId()).total())
                .isEqualByComparingTo("10000");
    }

    @Test
    void cheaperProductRequiresRefund() {
        UUID returnId = createPaidSaleAndReturn("10000");

        var exchange = exchanges.create(
                request(returnId, "8500", UUID.randomUUID())
        );

        assertThat(exchange.additionalPaymentDue())
                .isEqualByComparingTo("0");

        assertThat(exchange.refundDue())
                .isEqualByComparingTo("1500");

        assertThat(exchange.status())
                .isEqualTo(ExchangeStatus.PENDING_SETTLEMENT);
    }

    @Test
    void equalPriceCompletesAutomatically() {
        UUID returnId = createPaidSaleAndReturn("8500");

        var exchange = exchanges.create(
                request(returnId, "8500", UUID.randomUUID())
        );

        assertThat(exchange.additionalPaymentDue())
                .isEqualByComparingTo("0");

        assertThat(exchange.refundDue())
                .isEqualByComparingTo("0");

        assertThat(exchange.status())
                .isEqualTo(ExchangeStatus.COMPLETED);
    }

    @Test
    void sameRequestDoesNotCreateSecondSale() {
        UUID returnId = createPaidSaleAndReturn("8500");
        UUID key = UUID.randomUUID();

        var request = request(returnId, "10000", key);

        var first = exchanges.create(request);
        var second = exchanges.create(request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.newSaleId()).isEqualTo(first.newSaleId());

        assertThat(countExchanges(returnId)).isEqualTo(1);
    }

    @Test
    void sameReturnCannotBeExchangedTwice() {
        UUID returnId = createPaidSaleAndReturn("8500");

        exchanges.create(
                request(returnId, "10000", UUID.randomUUID())
        );

        assertThatThrownBy(() -> exchanges.create(
                request(returnId, "10000", UUID.randomUUID())
        )).isInstanceOfSatisfying(
                ExchangeRuleViolationException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo("RETURN_ALREADY_EXCHANGED")
        );

        assertThat(countExchanges(returnId)).isEqualTo(1);
    }

    @Test
    void refundedReturnCannotBeExchanged() {
        var originalSale = createPaidSale("8500");

        UUID returnId = createReturn(
                originalSale.id(),
                originalSale.items().getFirst().id()
        );

        refunds.create(
                new CreateRefundRequest(
                        originalSale.id(),
                        returnId,
                        new BigDecimal("8500"),
                        RefundMethod.CASH,
                        "Возврат денег",
                        null,
                        null,
                        UUID.randomUUID()
                )
        );

        assertThatThrownBy(() -> exchanges.create(
                request(returnId, "10000", UUID.randomUUID())
        )).isInstanceOfSatisfying(
                ExchangeRuleViolationException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo("RETURN_ALREADY_REFUNDED")
        );

        assertThat(countExchanges(returnId)).isZero();
    }

    private UUID createPaidSaleAndReturn(String price) {
        var sale = createPaidSale(price);

        return createReturn(
                sale.id(),
                sale.items().getFirst().id()
        );
    }

    @Test
    void insufficientStockRollsBackExchangeAndNewSale() {
        UUID returnId = createPaidSaleAndReturn("8500");

        UUID key = UUID.randomUUID();

        // Запрашиваем 100 кресел, хотя на складе их меньше.
        var exchangeRequest = new CreateExchangeRequest(
                key,
                returnId,
                FulfillmentType.SELF_PICKUP,
                List.of(
                        new CreateSaleItemRequest(
                                variantId,
                                warehouseId,
                                100,
                                new BigDecimal("10000")
                        )
                )
        );

        Long salesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales",
                Long.class
        );

        long reservedBefore = inventory
                .getBalance(warehouseId, variantId)
                .reserved();

        assertThatThrownBy(
                () -> exchanges.create(exchangeRequest)
        ).isInstanceOf(
                kg.chairx.inventory.domain.InsufficientStockException.class
        );

        assertThat(countExchanges(returnId)).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales",
                Long.class
        )).isEqualTo(salesBefore);

        assertThat(
                inventory.getBalance(warehouseId, variantId).reserved()
        ).isEqualTo(reservedBefore);
    }
    
    private kg.chairx.sale.api.SaleResponse createPaidSale(
            String price
    ) {
        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customerId,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variantId,
                                        warehouseId,
                                        1,
                                        new BigDecimal(price)
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

    private UUID createReturn(UUID saleId, UUID saleItemId) {
        return returns.create(
                new CreateReturnRequest(
                        saleId,
                        warehouseId,
                        UUID.randomUUID(),
                        List.of(
                                new CreateReturnItemRequest(
                                        saleItemId,
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        ),
                        "Exchange test",
                        null
                )
        ).id();
    }

    private CreateExchangeRequest request(
            UUID returnId,
            String newPrice,
            UUID key
    ) {
        return new CreateExchangeRequest(
                key,
                returnId,
                FulfillmentType.SELF_PICKUP,
                List.of(
                        new CreateSaleItemRequest(
                                variantId,
                                warehouseId,
                                1,
                                new BigDecimal(newPrice)
                        )
                )
        );
    }

    private long countExchanges(UUID returnId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchanges WHERE return_id = ?",
                Long.class,
                returnId
        );
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "exchange-test-user",
                        null,
                        List.of()
                )
        );
    }

    private void assertTestDatabase() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");
    }

    private void clean() {
        assertTestDatabase();

        jdbc.execute("""
                TRUNCATE TABLE
                    exchange_settlements, exchanges, refunds,
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
                    'EXCHANGE', 'REFUND', 'RETURN',
                    'PAYMENT', 'DELIVERY', 'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }
}