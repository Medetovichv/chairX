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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class RefundConcurrencyTests {

    @Autowired
    RefundService refunds;

    @Autowired
    PaymentService payments;

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
        authenticate("refund-concurrency-fixture");

        assertTestDatabase();
        clearData();

        home = jdbc.queryForObject(
                "select id from warehouses where code='HOME'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        customer = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    'Refund concurrency fixture',
                    true,
                    now(),
                    now()
                )
                """, product);

        jdbc.update("""
                insert into product_variants(
                    id,
                    product_id,
                    name,
                    recommended_sale_price,
                    active,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    ?,
                    'Black',
                    8500,
                    true,
                    now(),
                    now()
                )
                """, variant, product);

        jdbc.update("""
                insert into customers(
                    id,
                    full_name,
                    phone,
                    active,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    'Refund Concurrency Customer',
                    '+996555000001',
                    true,
                    now(),
                    now()
                )
                """, customer);

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        20,
                        "REFUND_CONCURRENCY_FIXTURE",
                        UUID.randomUUID(),
                        "refund-concurrency"
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        clearData();

        jdbc.update(
                "delete from customers where id=?",
                customer
        );

        jdbc.update(
                "delete from product_variants where id=?",
                variant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );

        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentRefundsCannotExceedPaidAmount()
            throws Exception {

        var sale = createSale();

        payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<RefundAttempt> first =
                    executor.submit(
                            () -> attemptRefund(
                                    sale.id(),
                                    "5000",
                                    "refund-concurrent-1",
                                    ready,
                                    start
                            )
                    );

            Future<RefundAttempt> second =
                    executor.submit(
                            () -> attemptRefund(
                                    sale.id(),
                                    "5000",
                                    "refund-concurrent-2",
                                    ready,
                                    start
                            )
                    );

            assertThat(
                    ready.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            start.countDown();

            RefundAttempt firstResult =
                    first.get(
                            10,
                            TimeUnit.SECONDS
                    );

            RefundAttempt secondResult =
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<RefundAttempt> results =
                    List.of(
                            firstResult,
                            secondResult
                    );

            assertThat(
                    results.stream()
                            .filter(
                                    RefundAttempt::success
                            )
                            .count()
            ).isEqualTo(1);

            assertThat(
                    results.stream()
                            .filter(
                                    result ->
                                            "REFUND_AMOUNT_EXCEEDED"
                                                    .equals(
                                                            result.errorCode()
                                                    )
                            )
                            .count()
            ).isEqualTo(1);

            assertThat(
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from refunds
                            where sale_id=?
                            """,
                            Long.class,
                            sale.id()
                    )
            ).isEqualTo(1);

            assertThat(
                    jdbc.queryForObject(
                            """
                            select coalesce(sum(amount), 0)
                            from refunds
                            where sale_id=?
                            """,
                            BigDecimal.class,
                            sale.id()
                    )
            ).isEqualByComparingTo("5000");

            assertThat(
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from audit_entries
                            where entity_type='REFUND'
                            """,
                            Long.class
                    )
            ).isEqualTo(1);
        } finally {
            start.countDown();

            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    private RefundAttempt attemptRefund(
            UUID saleId,
            String amount,
            String username,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            authenticate(username);

            ready.countDown();

            if (!start.await(
                    5,
                    TimeUnit.SECONDS
            )) {
                return RefundAttempt.failed(
                        "START_TIMEOUT"
                );
            }

            refunds.create(
                    new CreateRefundRequest(
                            saleId,
                            null,
                            new BigDecimal(amount),
                            RefundMethod.TRANSFER,
                            "Параллельный возврат",
                            null,
                            null,
                            UUID.randomUUID()
                    )
            );

            return RefundAttempt.succeeded();
        } catch (RefundRuleViolationException exception) {
            return RefundAttempt.failed(
                    exception.getCode()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            return RefundAttempt.failed(
                    "INTERRUPTED"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private kg.chairx.sale.api.SaleResponse createSale() {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        1,
                                        new BigDecimal("8500")
                                )
                        )
                )
        );
    }

    private void authenticate(String username) {
        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                username,
                                null,
                                List.of()
                        )
                );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    inventory_transfer_cost_origins, inventory_transfers, inventory_cost_movements, inventory_cost_allocations, inventory_cost_restorations, inventory_cost_write_offs, inventory_cost_layers, exchange_settlements, exchanges, refunds,
                    return_items,
                    returns,
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    stock_movements,
                    inventory_balances
                restart identity
                """);

        jdbc.execute(
                "alter sequence sale_number_seq restart with 1"
        );

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'REFUND',
                    'RETURN',
                    'PAYMENT',
                    'DELIVERY',
                    'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }

    private record RefundAttempt(
            boolean success,
            String errorCode
    ) {

        static RefundAttempt succeeded() {
            return new RefundAttempt(
                    true,
                    null
            );
        }

        static RefundAttempt failed(
                String errorCode
        ) {
            return new RefundAttempt(
                    false,
                    errorCode
            );
        }
    }
}