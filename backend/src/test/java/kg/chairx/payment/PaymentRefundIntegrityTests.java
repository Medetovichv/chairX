package kg.chairx.payment;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentRuleViolationException;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class PaymentRefundIntegrityTests {

    @Autowired
    PaymentService payments;

    @Autowired
    RefundService refunds;

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
        authenticate("payment-refund-fixture");

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
                    'Payment refund integrity fixture',
                    true,
                    now(),
                    now()
                )
                """,
                product
        );

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
                """,
                variant,
                product
        );

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
                    'Payment Refund Customer',
                    '+996555000003',
                    true,
                    now(),
                    now()
                )
                """,
                customer
        );

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        20,
                        "PAYMENT_REFUND_FIXTURE",
                        UUID.randomUUID(),
                        "payment-refund-fixture"
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
    void paymentCannotBeCancelledAfterRefund() {
        var sale = createSale();

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        refunds.create(
                refundRequest(
                        sale.id(),
                        "1000"
                )
        );

        assertThatThrownBy(
                () -> payments.cancel(
                        payment.id(),
                        new CancelPaymentRequest(
                                "Попытка отмены после возврата"
                        )
                )
        )
                .isInstanceOf(
                        PaymentRuleViolationException.class
                )
                .satisfies(
                        exception -> assertThat(
                                ((PaymentRuleViolationException) exception)
                                        .getCode()
                        ).isEqualTo(
                                "PAYMENT_HAS_REFUNDS"
                        )
                );

        var stored =
                payments.get(payment.id());

        assertThat(stored.status())
                .isEqualTo(PaymentStatus.PAID);

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
    }

    @Test
    void concurrentRefundAndPaymentCancellationRemainConsistent()
            throws Exception {

        var sale = createSale();

        var payment = payments.create(
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
            Future<OperationResult> refundFuture =
                    executor.submit(
                            () -> attemptRefund(
                                    sale.id(),
                                    ready,
                                    start
                            )
                    );

            Future<OperationResult> cancelFuture =
                    executor.submit(
                            () -> attemptCancellation(
                                    payment.id(),
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

            OperationResult refundResult =
                    refundFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            OperationResult cancelResult =
                    cancelFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            /*
             * Допустимы только два согласованных результата:
             *
             * 1. Refund первым блокирует Payment:
             *    Refund = SUCCESS
             *    Cancel = PAYMENT_HAS_REFUNDS
             *
             * 2. Cancel первым блокирует Payment:
             *    Cancel = SUCCESS
             *    Refund = SALE_NOT_PAID
             *
             * Состояние, где обе операции SUCCESS,
             * недопустимо.
             */

            boolean refundWon =
                    refundResult.success()
                            && "PAYMENT_HAS_REFUNDS"
                            .equals(
                                    cancelResult.errorCode()
                            );

            boolean cancellationWon =
                    cancelResult.success()
                            && "SALE_NOT_PAID"
                            .equals(
                                    refundResult.errorCode()
                            );

            assertThat(
                    refundWon || cancellationWon
            ).isTrue();

            long refundCount =
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from refunds
                            where sale_id=?
                            """,
                            Long.class,
                            sale.id()
                    );

            String paymentStatus =
                    jdbc.queryForObject(
                            """
                            select status
                            from payments
                            where id=?
                            """,
                            String.class,
                            payment.id()
                    );

            if (refundWon) {
                assertThat(refundCount)
                        .isEqualTo(1);

                assertThat(paymentStatus)
                        .isEqualTo("PAID");
            }

            if (cancellationWon) {
                assertThat(refundCount)
                        .isZero();

                assertThat(paymentStatus)
                        .isEqualTo("CANCELLED");
            }

            assertThat(
                    refundCount == 1
                            && "CANCELLED".equals(
                            paymentStatus
                    )
            ).isFalse();

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

    private OperationResult attemptRefund(
            UUID saleId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            authenticate(
                    "concurrent-refund-user"
            );

            ready.countDown();

            if (!start.await(
                    5,
                    TimeUnit.SECONDS
            )) {
                return OperationResult.failed(
                        "START_TIMEOUT"
                );
            }

            refunds.create(
                    refundRequest(
                            saleId,
                            "1000"
                    )
            );

            return OperationResult.succeeded();

        } catch (RefundRuleViolationException exception) {
            return OperationResult.failed(
                    exception.getCode()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            return OperationResult.failed(
                    "INTERRUPTED"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private OperationResult attemptCancellation(
            UUID paymentId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            authenticate(
                    "concurrent-cancel-user"
            );

            ready.countDown();

            if (!start.await(
                    5,
                    TimeUnit.SECONDS
            )) {
                return OperationResult.failed(
                        "START_TIMEOUT"
                );
            }

            payments.cancel(
                    paymentId,
                    new CancelPaymentRequest(
                            "Параллельная отмена оплаты"
                    )
            );

            return OperationResult.succeeded();

        } catch (PaymentRuleViolationException exception) {
            return OperationResult.failed(
                    exception.getCode()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            return OperationResult.failed(
                    "INTERRUPTED"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CreateRefundRequest refundRequest(
            UUID saleId,
            String amount
    ) {
        return new CreateRefundRequest(
                saleId,
                null,
                new BigDecimal(amount),
                RefundMethod.TRANSFER,
                "Возврат клиенту",
                null,
                null,
                UUID.randomUUID()
        );
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

    private void authenticate(
            String username
    ) {
        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken
                                .authenticated(
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

    private record OperationResult(
            boolean success,
            String errorCode
    ) {

        static OperationResult succeeded() {
            return new OperationResult(
                    true,
                    null
            );
        }

        static OperationResult failed(
                String errorCode
        ) {
            return new OperationResult(
                    false,
                    errorCode
            );
        }
    }
}