package kg.chairx.payment;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentRuleViolationException;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentStatus;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.application.SaleRuleViolationException;
import kg.chairx.sale.domain.SaleStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.extension.ExtendWith(kg.chairx.FundedFinanceExtension.class)
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class PaymentTests {

    @Autowired
    PaymentService payments;

    @Autowired
    SaleService sales;

    @Autowired
    InventoryService inventory;

    @Autowired
    InventoryAdjustmentService adjustments;

    @Autowired
    JdbcTemplate jdbc;

    UUID home;
    UUID product;
    UUID variant;
    UUID customer;

    @BeforeEach
    void fixture() {
        authenticate("payment-test-user");

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
                values (?, 'Payment fixture', true, now(), now())
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
                values (?, ?, 'Black', 8500, true, now(), now())
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
                    'Payment Customer',
                    '+996555000000',
                    true,
                    now(),
                    now()
                )
                """, customer);

        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                home,
                variant,
                20,
                BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(20)),
                "payment-test"
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
    void paymentAmountIsDerivedFromSaleTotal() {
        var sale = createSale(
                3,
                "8500"
        );

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        "MBANK-123",
                        "Оплата клиента"
                )
        );

        assertThat(payment.id())
                .isNotNull();

        assertThat(payment.saleId())
                .isEqualTo(sale.id());

        assertThat(payment.amount())
                .isEqualByComparingTo("25500");

        assertThat(payment.method())
                .isEqualTo(PaymentMethod.TRANSFER);

        assertThat(payment.status())
                .isEqualTo(PaymentStatus.PAID);

        assertThat(payment.paidBy())
                .isEqualTo("payment-test-user");

        assertThat(payment.paidAt())
                .isNotNull();

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from payments where sale_id=?",
                        Integer.class,
                        sale.id()
                )
        ).isEqualTo(1);

        assertThat(paymentAuditCount())
                .isEqualTo(1);
    }

    @Test
    void cashPaymentIsSupported() {
        var sale = createSale(
                1,
                "8500"
        );

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.CASH,
                        null,
                        null
                )
        );

        assertThat(payment.method())
                .isEqualTo(PaymentMethod.CASH);

        assertThat(payment.amount())
                .isEqualByComparingTo("8500");

        assertThat(payment.status())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void secondActivePaymentForSameSaleIsRejected() {
        var sale = createSale(
                1,
                "8500"
        );

        payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        assertThatThrownBy(
                () -> payments.create(
                        new CreatePaymentRequest(
                                sale.id(),
                                PaymentMethod.CASH,
                                null,
                                null
                        )
                )
        )
                .isInstanceOfSatisfying(
                        PaymentRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("SALE_ALREADY_PAID")
                );

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from payments
                        where sale_id=?
                          and status='PAID'
                        """,
                        Integer.class,
                        sale.id()
                )
        ).isEqualTo(1);
    }

    @Test
    void concurrentPaymentRegistrationCreatesOnlyOneActivePayment()
            throws Exception {

        var sale = createSale(
                1,
                "8500"
        );

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<PaymentAttempt> first =
                    executor.submit(
                            () -> attemptConcurrentPayment(
                                    sale.id(),
                                    PaymentMethod.CASH,
                                    "payment-concurrent-1",
                                    ready,
                                    start
                            )
                    );

            Future<PaymentAttempt> second =
                    executor.submit(
                            () -> attemptConcurrentPayment(
                                    sale.id(),
                                    PaymentMethod.TRANSFER,
                                    "payment-concurrent-2",
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

            PaymentAttempt firstResult =
                    first.get(
                            10,
                            TimeUnit.SECONDS
                    );

            PaymentAttempt secondResult =
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    );

            assertThat(
                    List.of(
                                    firstResult,
                                    secondResult
                            ).stream()
                            .filter(PaymentAttempt::success)
                            .count()
            ).isEqualTo(1);

            assertThat(
                    List.of(
                                    firstResult,
                                    secondResult
                            ).stream()
                            .filter(
                                    result ->
                                            "SALE_ALREADY_PAID".equals(
                                                    result.errorCode()
                                            )
                            )
                            .count()
            ).isEqualTo(1);

            assertThat(
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from payments
                            where sale_id=?
                              and status='PAID'
                            """,
                            Integer.class,
                            sale.id()
                    )
            ).isEqualTo(1);

            assertThat(
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from payments
                            where sale_id=?
                            """,
                            Integer.class,
                            sale.id()
                    )
            ).isEqualTo(1);

            assertThat(paymentAuditCount())
                    .isEqualTo(1);
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

    @Test
    void cancelledSaleCannotBePaid() {
        var sale = createSale(
                1,
                "8500"
        );

        sales.cancel(sale.id());

        assertThatThrownBy(
                () -> payments.create(
                        new CreatePaymentRequest(
                                sale.id(),
                                PaymentMethod.CASH,
                                null,
                                null
                        )
                )
        )
                .isInstanceOfSatisfying(
                        PaymentRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("SALE_CANCELLED")
                );

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from payments",
                        Integer.class
                )
        ).isZero();
    }

    @Test
    void fulfilledSaleCanBePaid() {
        var sale = createSale(
                1,
                "8500"
        );

        sales.fulfill(sale.id());

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        "Оплата после выдачи"
                )
        );

        assertThat(payment.status())
                .isEqualTo(PaymentStatus.PAID);

        assertThat(payment.amount())
                .isEqualByComparingTo("8500");
    }

    @Test
    void cancellingPaymentKeepsHistory() {
        var sale = createSale(
                1,
                "8500"
        );

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        "WRONG-REFERENCE",
                        null
                )
        );

        var cancelled = payments.cancel(
                payment.id(),
                new CancelPaymentRequest(
                        "Ошибочно указан способ оплаты"
                )
        );

        assertThat(cancelled.status())
                .isEqualTo(PaymentStatus.CANCELLED);

        assertThat(cancelled.cancelledBy())
                .isEqualTo("payment-test-user");

        assertThat(cancelled.cancelledAt())
                .isNotNull();

        assertThat(cancelled.cancellationReason())
                .isEqualTo(
                        "Ошибочно указан способ оплаты"
                );

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from payments
                        where id=?
                          and status='CANCELLED'
                        """,
                        Integer.class,
                        payment.id()
                )
        ).isEqualTo(1);

        assertThat(paymentAuditCount())
                .isEqualTo(2);
    }

    @Test
    void newPaymentCanBeCreatedAfterPreviousPaymentWasCancelled() {
        var sale = createSale(
                1,
                "8500"
        );

        var first = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        payments.cancel(
                first.id(),
                new CancelPaymentRequest(
                        "Оплата зарегистрирована ошибочно"
                )
        );

        var second = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.CASH,
                        null,
                        null
                )
        );

        assertThat(second.id())
                .isNotEqualTo(first.id());

        assertThat(second.method())
                .isEqualTo(PaymentMethod.CASH);

        assertThat(second.status())
                .isEqualTo(PaymentStatus.PAID);

        var history = payments.getBySale(
                sale.id()
        );

        assertThat(history)
                .hasSize(2);

        assertThat(history)
                .anySatisfy(payment -> {
                    assertThat(payment.id())
                            .isEqualTo(first.id());

                    assertThat(payment.status())
                            .isEqualTo(
                                    PaymentStatus.CANCELLED
                            );
                });

        assertThat(history)
                .anySatisfy(payment -> {
                    assertThat(payment.id())
                            .isEqualTo(second.id());

                    assertThat(payment.status())
                            .isEqualTo(
                                    PaymentStatus.PAID
                            );
                });

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from payments
                        where sale_id=?
                          and status='PAID'
                        """,
                        Integer.class,
                        sale.id()
                )
        ).isEqualTo(1);
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        var sale = createSale(
                1,
                "8500"
        );

        var payment = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.CASH,
                        null,
                        null
                )
        );

        var first = payments.cancel(
                payment.id(),
                new CancelPaymentRequest(
                        "Ошибка кассира"
                )
        );

        var second = payments.cancel(
                payment.id(),
                new CancelPaymentRequest(
                        "Повторный запрос"
                )
        );

        assertThat(second.status())
                .isEqualTo(PaymentStatus.CANCELLED);

        assertThat(second.cancelledAt())
                .isEqualTo(first.cancelledAt());

        assertThat(second.cancellationReason())
                .isEqualTo(first.cancellationReason());

        assertThat(paymentAuditCount())
                .isEqualTo(2);
    }

    @Test
    void activePaymentCanBeRetrievedBySale() {
        var sale = createSale(
                2,
                "8500"
        );

        var created = payments.create(
                new CreatePaymentRequest(
                        sale.id(),
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );

        var active = payments.getActiveBySale(
                sale.id()
        );

        assertThat(active.id())
                .isEqualTo(created.id());

        assertThat(active.amount())
                .isEqualByComparingTo("17000");

        assertThat(active.status())
                .isEqualTo(PaymentStatus.PAID);
    }


    @Test
    void paidSaleCancellationIsRejectedWithoutSideEffects() {
        var sale = createSale(2, "8500");
        var payment = payments.create(new CreatePaymentRequest(
                sale.id(), PaymentMethod.CASH, null, null));
        int reservedBefore = inventory.getBalance(home, variant).reserved() > 0 ? 2 : -1;
        Long movementsBefore = jdbc.queryForObject(
                "select count(*) from finance_movements", Long.class);

        assertThatThrownBy(() -> sales.cancel(sale.id()))
                .isInstanceOfSatisfying(SaleRuleViolationException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SALE_HAS_ACTIVE_PAYMENT"));

        assertThat(sales.get(sale.id()).status()).isEqualTo(SaleStatus.CONFIRMED);
        assertThat(payments.get(payment.id()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(inventory.getBalance(home, variant).reserved()).isEqualTo(reservedBefore);
        assertThat(jdbc.queryForObject("select count(*) from finance_movements", Long.class))
                .isEqualTo(movementsBefore);
    }

    @Test
    void saleMayBeCancelledOncePaymentWasReversed() {
        var sale = createSale(1, "8500");
        var payment = payments.create(new CreatePaymentRequest(
                sale.id(), PaymentMethod.CASH, null, null));
        payments.cancel(payment.id(), new CancelPaymentRequest("Ошибочная оплата"));

        assertThat(sales.cancel(sale.id()).status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(inventory.getBalance(home, variant).reserved()).isZero();
        assertThat(payments.get(payment.id()).status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void repeatedCancellationDoesNotReleaseStockTwice() {
        var sale = createSale(2, "8500");
        assertThat(sales.cancel(sale.id()).status()).isEqualTo(SaleStatus.CANCELLED);
        int remaining = inventory.getBalance(home, variant).reserved();
        assertThat(sales.cancel(sale.id()).status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(remaining).isZero();
        assertThat(inventory.getBalance(home, variant).reserved()).isZero();
    }

    @Test
    void concurrentPaymentAndCancellationAreSerializedBySaleLock() throws Exception {
        var sale = createSale(1, "8500");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> paymentTask = executor.submit(() -> {
                authenticate("p18-payment");
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) return "TIMEOUT";
                    payments.create(new CreatePaymentRequest(sale.id(), PaymentMethod.CASH, null, null));
                    return "PAID";
                } catch (PaymentRuleViolationException e) {
                    return e.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Future<String> cancelTask = executor.submit(() -> {
                authenticate("p18-cancel");
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) return "TIMEOUT";
                    sales.cancel(sale.id());
                    return "CANCELLED";
                } catch (SaleRuleViolationException e) {
                    return e.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            String paymentResult = paymentTask.get(15, TimeUnit.SECONDS);
            String cancelResult = cancelTask.get(15, TimeUnit.SECONDS);
            assertThat(List.of(paymentResult, cancelResult)).containsAnyOf("PAID", "CANCELLED");
            assertThat(List.of(paymentResult, cancelResult))
                    .satisfies(results -> {
                        if ("PAID".equals(paymentResult)) {
                            assertThat(cancelResult).isEqualTo("SALE_HAS_ACTIVE_PAYMENT");
                        } else {
                            assertThat(paymentResult).isEqualTo("SALE_CANCELLED");
                            assertThat(cancelResult).isEqualTo("CANCELLED");
                        }
                    });
            int active = jdbc.queryForObject(
                    "select count(*) from payments where sale_id=? and status='PAID'",
                    Integer.class, sale.id());
            if (sales.get(sale.id()).status() == SaleStatus.CANCELLED) {
                assertThat(active).isZero();
                assertThat(inventory.getBalance(home, variant).reserved()).isZero();
            } else {
                assertThat(active).isEqualTo(1);
                assertThat(inventory.getBalance(home, variant).reserved()).isEqualTo(1);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private PaymentAttempt attemptConcurrentPayment(
            UUID saleId,
            PaymentMethod method,
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
                return PaymentAttempt.failed(
                        "START_TIMEOUT"
                );
            }

            payments.create(
                    new CreatePaymentRequest(
                            saleId,
                            method,
                            null,
                            null
                    )
            );

            return PaymentAttempt.succeeded();
        } catch (PaymentRuleViolationException exception) {
            return PaymentAttempt.failed(
                    exception.getCode()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return PaymentAttempt.failed(
                    "INTERRUPTED"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private kg.chairx.sale.api.SaleResponse createSale(
            long quantity,
            String unitSalePrice
    ) {
        return sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.SELF_PICKUP,
                        List.of(
                                new CreateSaleItemRequest(
                                        variant,
                                        home,
                                        quantity,
                                        new BigDecimal(
                                                unitSalePrice
                                        )
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
                    return_items, returns, payments,
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
                    'PAYMENT',
                    'SALE',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private long paymentAuditCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from audit_entries
                where entity_type='PAYMENT'
                """,
                Long.class
        );
    }

    private void assertTestDatabase() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }

    private record PaymentAttempt(
            boolean success,
            String errorCode
    ) {

        static PaymentAttempt succeeded() {
            return new PaymentAttempt(
                    true,
                    null
            );
        }

        static PaymentAttempt failed(
                String errorCode
        ) {
            return new PaymentAttempt(
                    false,
                    errorCode
            );
        }
    }

    @Test
    void paymentAndReversalPostToBankExactlyOnce() {
        var sale = createSale(1, "8500");
        BigDecimal before = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class);
        var payment = payments.create(new CreatePaymentRequest(sale.id(), PaymentMethod.TRANSFER, null, null));
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class))
                .isEqualByComparingTo(before.add(new BigDecimal("8500")));
        payments.cancel(payment.id(), new CancelPaymentRequest("Ошибочная оплата"));
        payments.cancel(payment.id(), new CancelPaymentRequest("Повтор"));
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'", BigDecimal.class))
                .isEqualByComparingTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements WHERE source_id=?", Integer.class, payment.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM finance_movements WHERE source_id=?", BigDecimal.class, payment.id())).isZero();
    }

    @Test
    void paymentOnUninitializedAccountRollsBackDocumentAndAudit() {
        var sale = createSale(1, "8500");
        jdbc.update("UPDATE finance_accounts SET opening_balance_initialized=FALSE WHERE code='CASH'");
        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(sale.id(), PaymentMethod.CASH, null, null)))
                .isInstanceOf(kg.chairx.finance.application.FinancePostingException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE sale_id=?", Integer.class, sale.id())).isZero();
        assertThat(paymentAuditCount()).isZero();
    }

    @Test
    void legacyPaymentCannotSilentlyDebitAccountOnCancellation() {
        var sale = createSale(1, "8500");
        var payment = payments.create(new CreatePaymentRequest(sale.id(), PaymentMethod.CASH, null, null));
        jdbc.update("DELETE FROM finance_movements WHERE source_id=?", payment.id());
        BigDecimal before = jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class);
        assertThatThrownBy(() -> payments.cancel(payment.id(), new CancelPaymentRequest("Историческая оплата")))
                .isInstanceOf(kg.chairx.finance.application.FinancePostingException.class)
                .hasMessageContaining("сверки");
        assertThat(payments.get(payment.id()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'", BigDecimal.class)).isEqualByComparingTo(before);
    }

}