package kg.chairx.exchange;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.TestDatabaseCleaner;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.application.ExchangeService;
import kg.chairx.exchange.application.ExchangeSettlementService;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentRuleViolationException;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.application.ReturnService;
import kg.chairx.returning.domain.ReturnCondition;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleRuleViolationException;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class ExchangeWorkflowTests {

    @Autowired ExchangeService exchanges;
    @Autowired ExchangeSettlementService settlements;
    @Autowired SaleService sales;
    @Autowired PaymentService payments;
    @Autowired ReturnService returns;
    @Autowired InventoryService inventory;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    UUID warehouseId;
    UUID productId;
    UUID variantId;
    UUID customerId;

    @BeforeEach
    void setup() {
        authenticate();
        assertTestDatabase();

        TestDatabaseCleaner.clean(jdbc);

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
                VALUES (?, 'Exchange workflow product',
                        true, now(), now())
                """, productId);

        jdbc.update("""
                INSERT INTO product_variants
                    (id, product_id, name,
                     recommended_sale_price,
                     active, created_at, updated_at)
                VALUES (?, ?, 'Black', 8500,
                        true, now(), now())
                """, variantId, productId);

        jdbc.update("""
                INSERT INTO customers
                    (id, full_name, phone,
                     active, created_at, updated_at)
                VALUES (?, 'Exchange Workflow Customer',
                        '+996555333333', true, now(), now())
                """, customerId);

        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                warehouseId,
                variantId,
                30,
                BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(30)),
                "exchange-test"
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        TestDatabaseCleaner.clean(jdbc);

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
    void fulfillmentWaitsForExchangeRowLock()
            throws Exception {

        var exchange = createExchange("8500", "10000");

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch fulfillmentStarted = new CountDownLatch(1);

        AtomicBoolean fulfillmentFinished = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try {
            Future<Void> lockFuture = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    jdbc.queryForObject(
                            """
                            SELECT id
                            FROM exchanges
                            WHERE id = ?
                            FOR UPDATE
                            """,
                            UUID.class,
                            exchange.id()
                    );

                    lockAcquired.countDown();

                    try {
                        if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting to release lock"
                            );
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });

                return null;
            });

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS))
                    .isTrue();

            Future<String> fulfillmentFuture = executor.submit(() -> {
                authenticate();

                try {
                    fulfillmentStarted.countDown();

                    try {
                        sales.fulfill(exchange.newSaleId());
                        return "FULFILLED";
                    } catch (SaleRuleViolationException exception) {
                        return exception.getCode();
                    } finally {
                        fulfillmentFinished.set(true);
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertThat(fulfillmentStarted.await(5, TimeUnit.SECONDS))
                    .isTrue();

            // Даём второй операции время дойти до блокировки.
            Thread.sleep(300);

            assertThat(fulfillmentFinished.get())
                    .as("Fulfillment must wait while exchange row is locked")
                    .isFalse();

            releaseLock.countDown();

            lockFuture.get(10, TimeUnit.SECONDS);

            assertThat(fulfillmentFuture.get(10, TimeUnit.SECONDS))
                    .isEqualTo("EXCHANGE_SETTLEMENT_REQUIRED");

            assertThat(sales.get(exchange.newSaleId()).status())
                    .isEqualTo(SaleStatus.CONFIRMED);

            assertThat(exchanges.get(exchange.id()).status())
                    .isEqualTo(ExchangeStatus.PENDING_SETTLEMENT);

        } finally {
            releaseLock.countDown();

            executor.shutdownNow();

            assertThat(executor.awaitTermination(
                    5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void exchangeSaleCannotBePaidThroughRegularPayment() {
        var exchange = createExchange("8500", "10000");

        assertThatThrownBy(() -> payments.create(
                new CreatePaymentRequest(
                        exchange.newSaleId(),
                        PaymentMethod.CASH,
                        null,
                        null
                )
        )).isInstanceOfSatisfying(
                PaymentRuleViolationException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo("EXCHANGE_SALE_PAYMENT_FORBIDDEN")
        );

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM payments
                WHERE sale_id = ?
                """,
                Long.class,
                exchange.newSaleId()
        )).isZero();
    }

    @Test
    void exchangeSaleCannotBeFulfilledBeforeSettlement() {
        var exchange = createExchange("8500", "10000");

        assertThatThrownBy(() ->
                sales.fulfill(exchange.newSaleId())
        ).isInstanceOfSatisfying(
                SaleRuleViolationException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo("EXCHANGE_SETTLEMENT_REQUIRED")
        );

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.CONFIRMED);
    }

    @Test
    void exchangeSaleCannotBeCancelledNormally() {
        var exchange = createExchange("8500", "10000");

        assertThatThrownBy(() ->
                sales.cancel(exchange.newSaleId())
        ).isInstanceOfSatisfying(
                SaleRuleViolationException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo("EXCHANGE_SALE_CANCELLATION_FORBIDDEN")
        );

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.CONFIRMED);
    }

    @Test
    void completedSettlementAllowsFulfillment() {
        var exchange = createExchange("8500", "10000");

        settlements.settle(
                exchange.id(),
                UUID.randomUUID(),
                "IN",
                "CASH",
                new BigDecimal("1500"),
                "Доплата",
                "exchange-test-user"
        );

        assertThat(exchanges.get(exchange.id()).status())
                .isEqualTo(ExchangeStatus.COMPLETED);

        sales.fulfill(exchange.newSaleId());

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.FULFILLED);
    }

    @Test
    void concurrentSettlementAndFulfillmentRemainConsistent()
            throws Exception {

        var exchange = createExchange("8500", "10000");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> settlementFuture = executor.submit(() -> {
                authenticate();
                try {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        return "START_TIMEOUT";
                    }

                    settlements.settle(
                            exchange.id(),
                            UUID.randomUUID(),
                            "IN",
                            "CASH",
                            new BigDecimal("1500"),
                            null,
                            "exchange-test-user"
                    );

                    return "SETTLED";
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            Future<String> fulfillmentFuture = executor.submit(() -> {
                authenticate();
                try {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        return "START_TIMEOUT";
                    }

                    try {
                        sales.fulfill(exchange.newSaleId());
                        return "FULFILLED";
                    } catch (SaleRuleViolationException exception) {
                        if ("EXCHANGE_SETTLEMENT_REQUIRED"
                                .equals(exception.getCode())) {
                            return "SETTLEMENT_REQUIRED";
                        }
                        throw exception;
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            String settlementResult =
                    settlementFuture.get(15, TimeUnit.SECONDS);

            String fulfillmentResult =
                    fulfillmentFuture.get(15, TimeUnit.SECONDS);

            assertThat(settlementResult).isEqualTo("SETTLED");

            assertThat(fulfillmentResult)
                    .isIn("FULFILLED", "SETTLEMENT_REQUIRED");

            assertThat(exchanges.get(exchange.id()).status())
                    .isEqualTo(ExchangeStatus.COMPLETED);

            if ("SETTLEMENT_REQUIRED".equals(fulfillmentResult)) {
                assertThat(sales.get(exchange.newSaleId()).status())
                        .isEqualTo(SaleStatus.CONFIRMED);

                sales.fulfill(exchange.newSaleId());
            }

            assertThat(sales.get(exchange.newSaleId()).status())
                    .isEqualTo(SaleStatus.FULFILLED);

            assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM exchange_settlements
                WHERE exchange_id = ?
                """,
                    Long.class,
                    exchange.id()
            )).isEqualTo(1);

            assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM exchange_settlements
                WHERE exchange_id = ?
                  AND direction = 'IN'
                """,
                    BigDecimal.class,
                    exchange.id()
            )).isEqualByComparingTo("1500");

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(
                    5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void partialSettlementDoesNotAllowFulfillment() {
        var exchange = createExchange("8500", "10000");

        settlements.settle(
                exchange.id(),
                UUID.randomUUID(),
                "IN",
                "CASH",
                new BigDecimal("1000"),
                null,
                "exchange-test-user"
        );

        assertThatThrownBy(() ->
                sales.fulfill(exchange.newSaleId())
        ).isInstanceOfSatisfying(
                SaleRuleViolationException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo("EXCHANGE_SETTLEMENT_REQUIRED")
        );

        settlements.settle(
                exchange.id(),
                UUID.randomUUID(),
                "IN",
                "TRANSFER",
                new BigDecimal("500"),
                null,
                "exchange-test-user"
        );

        sales.fulfill(exchange.newSaleId());

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.FULFILLED);
    }

    @Test
    void equalPriceExchangeCanBeFulfilledWithoutSettlement() {
        var exchange = createExchange("8500", "8500");

        assertThat(exchange.status())
                .isEqualTo(ExchangeStatus.COMPLETED);

        sales.fulfill(exchange.newSaleId());

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.FULFILLED);
    }

    @Test
    void cheaperExchangeRequiresRefundBeforeFulfillment() {
        var exchange = createExchange("10000", "8500");

        assertThatThrownBy(() ->
                sales.fulfill(exchange.newSaleId())
        ).isInstanceOf(SaleRuleViolationException.class);

        settlements.settle(
                exchange.id(),
                UUID.randomUUID(),
                "OUT",
                "TRANSFER",
                new BigDecimal("1500"),
                null,
                "exchange-test-user"
        );

        sales.fulfill(exchange.newSaleId());

        assertThat(sales.get(exchange.newSaleId()).status())
                .isEqualTo(SaleStatus.FULFILLED);
    }

    private kg.chairx.exchange.api.ExchangeResponse createExchange(
            String originalPrice,
            String newPrice
    ) {
        var originalSale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customerId,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variantId,
                                        warehouseId,
                                        1,
                                        new BigDecimal(originalPrice)
                                )
                        )
                )
        );

        payments.create(
                new CreatePaymentRequest(
                        originalSale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        sales.fulfill(originalSale.id());

        var returned = returns.create(
                new CreateReturnRequest(
                        originalSale.id(),
                        warehouseId,
                        UUID.randomUUID(),
                        List.of(
                                new CreateReturnItemRequest(
                                        originalSale.items().getFirst().id(),
                                        1,
                                        ReturnCondition.SELLABLE
                                )
                        ),
                        "Exchange workflow",
                        null
                )
        );

        return exchanges.create(
                new CreateExchangeRequest(
                        UUID.randomUUID(),
                        returned.id(),
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variantId,
                                        warehouseId,
                                        1,
                                        new BigDecimal(newPrice)
                                )
                        )
                )
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
}