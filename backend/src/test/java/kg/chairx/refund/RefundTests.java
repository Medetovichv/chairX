package kg.chairx.refund;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.refund.api.CreateRefundRequest;
import kg.chairx.refund.application.RefundNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class RefundTests {

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
    InventoryAdjustmentService adjustments;

    @Autowired
    JdbcTemplate jdbc;

    UUID home;
    UUID product;
    UUID variant;
    UUID customer;

    @BeforeEach
    void fixture() {
        authenticate("refund-test-user");

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
                values (?, 'Refund fixture', true, now(), now())
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
                    'Refund Customer',
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
                "refund-test"
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
    void fullRefundCanBeCreatedForPaidSale() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        var refund = refunds.create(
                request(
                        sale.id(),
                        null,
                        "8500",
                        RefundMethod.TRANSFER,
                        "Полный возврат клиенту",
                        UUID.randomUUID()
                )
        );

        assertThat(refund.id())
                .isNotNull();

        assertThat(refund.saleId())
                .isEqualTo(sale.id());

        assertThat(refund.returnId())
                .isNull();

        assertThat(refund.amount())
                .isEqualByComparingTo("8500");

        assertThat(refund.method())
                .isEqualTo(RefundMethod.TRANSFER);

        assertThat(refund.reason())
                .isEqualTo("Полный возврат клиенту");

        assertThat(refund.refundedBy())
                .isEqualTo("refund-test-user");

        assertThat(refund.refundedAt())
                .isNotNull();

        assertThat(refundCount(sale.id()))
                .isEqualTo(1);

        assertThat(refundAuditCount())
                .isEqualTo(1);
    }

    @Test
    void partialRefundCanBeCreated() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        var refund = refunds.create(
                request(
                        sale.id(),
                        null,
                        "2500",
                        RefundMethod.CASH,
                        "Частичная компенсация",
                        UUID.randomUUID()
                )
        );

        assertThat(refund.amount())
                .isEqualByComparingTo("2500");

        assertThat(refund.method())
                .isEqualTo(RefundMethod.CASH);

        assertThat(refundedAmount(sale.id()))
                .isEqualByComparingTo("2500");
    }

    @Test
    void multiplePartialRefundsCanReachPaymentAmount() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "3000",
                        RefundMethod.CASH,
                        "Первая компенсация",
                        UUID.randomUUID()
                )
        );

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "2000",
                        RefundMethod.TRANSFER,
                        "Вторая компенсация",
                        UUID.randomUUID()
                )
        );

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "3500",
                        RefundMethod.TRANSFER,
                        "Окончательный возврат",
                        UUID.randomUUID()
                )
        );

        assertThat(refundCount(sale.id()))
                .isEqualTo(3);

        assertThat(refundedAmount(sale.id()))
                .isEqualByComparingTo("8500");

        assertThat(refundAuditCount())
                .isEqualTo(3);
    }

    @Test
    void refundCannotExceedPaymentAmount() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "6000",
                        RefundMethod.CASH,
                        "Первый возврат",
                        UUID.randomUUID()
                )
        );

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                sale.id(),
                                null,
                                "3000",
                                RefundMethod.CASH,
                                "Лишний возврат",
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "REFUND_AMOUNT_EXCEEDED"
                                )
                );

        assertThat(refundedAmount(sale.id()))
                .isEqualByComparingTo("6000");

        assertThat(refundCount(sale.id()))
                .isEqualTo(1);
    }

    @Test
    void saleWithoutActivePaymentCannotBeRefunded() {
        var sale = createSale(1, "8500");

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                sale.id(),
                                null,
                                "1000",
                                RefundMethod.CASH,
                                "Возврат без оплаты",
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("SALE_NOT_PAID")
                );

        assertThat(refundCount(sale.id()))
                .isZero();
    }

    @Test
    void cancelledPaymentCannotBeRefunded() {
        var sale = createSale(1, "8500");

        var payment = createPayment(sale.id());

        payments.cancel(
                payment.id(),
                new kg.chairx.payment.api.CancelPaymentRequest(
                        "Оплата зарегистрирована ошибочно"
                )
        );

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                sale.id(),
                                null,
                                "1000",
                                RefundMethod.CASH,
                                "Попытка возврата",
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("SALE_NOT_PAID")
                );

        assertThat(refundCount(sale.id()))
                .isZero();
    }

    @Test
    void refundCanReferenceReturnFromSameSale() {
        var sale = createSale(1, "8500");

        createPayment(sale.id());

        sales.fulfill(sale.id());

        var saleReturn = createReturn(
                sale.id(),
                sale.items().getFirst().id(),
                1
        );

        var refund = refunds.create(
                request(
                        sale.id(),
                        saleReturn.id(),
                        "7500",
                        RefundMethod.TRANSFER,
                        "Возврат с удержанием за повреждение",
                        UUID.randomUUID()
                )
        );

        assertThat(refund.returnId())
                .isEqualTo(saleReturn.id());

        assertThat(refund.amount())
                .isEqualByComparingTo("7500");
    }

    @Test
    void returnFromDifferentSaleCannotBeReferenced() {
        var firstSale = createSale(1, "8500");
        var secondSale = createSale(1, "8500");

        createPayment(firstSale.id());

        sales.fulfill(secondSale.id());

        var secondReturn = createReturn(
                secondSale.id(),
                secondSale.items().getFirst().id(),
                1
        );

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                firstSale.id(),
                                secondReturn.id(),
                                "1000",
                                RefundMethod.CASH,
                                "Некорректная связь",
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "RETURN_SALE_MISMATCH"
                                )
                );

        assertThat(refundCount(firstSale.id()))
                .isZero();
    }

    @Test
    void missingReturnIsRejected() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                sale.id(),
                                UUID.randomUUID(),
                                "1000",
                                RefundMethod.CASH,
                                "Несуществующий возврат",
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("RETURN_NOT_FOUND")
                );

        assertThat(refundCount(sale.id()))
                .isZero();
    }

    @Test
    void sameIdempotencyKeyWithSamePayloadReturnsSameRefund() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        UUID key = UUID.randomUUID();

        CreateRefundRequest request =
                request(
                        sale.id(),
                        null,
                        "2500",
                        RefundMethod.TRANSFER,
                        "Компенсация",
                        key
                );

        var first = refunds.create(request);
        var second = refunds.create(request);

        assertThat(second.id())
                .isEqualTo(first.id());

        assertThat(second.refundedAt())
                .isEqualTo(first.refundedAt());

        assertThat(refundCount(sale.id()))
                .isEqualTo(1);

        assertThat(refundedAmount(sale.id()))
                .isEqualByComparingTo("2500");

        assertThat(refundAuditCount())
                .isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        UUID key = UUID.randomUUID();

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "2000",
                        RefundMethod.CASH,
                        "Компенсация",
                        key
                )
        );

        assertThatThrownBy(
                () -> refunds.create(
                        request(
                                sale.id(),
                                null,
                                "3000",
                                RefundMethod.CASH,
                                "Компенсация",
                                key
                        )
                )
        )
                .isInstanceOfSatisfying(
                        RefundRuleViolationException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(
                                        "IDEMPOTENCY_KEY_REUSED"
                                )
                );

        assertThat(refundCount(sale.id()))
                .isEqualTo(1);

        assertThat(refundedAmount(sale.id()))
                .isEqualByComparingTo("2000");
    }

    @Test
    void refundCanBeRetrievedById() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        var created = refunds.create(
                request(
                        sale.id(),
                        null,
                        "1500",
                        RefundMethod.CASH,
                        "Частичный возврат",
                        UUID.randomUUID()
                )
        );

        var loaded = refunds.get(created.id());

        assertThat(loaded.id())
                .isEqualTo(created.id());

        assertThat(loaded.saleId())
                .isEqualTo(sale.id());

        assertThat(loaded.amount())
                .isEqualByComparingTo("1500");
    }

    @Test
    void refundsCanBeRetrievedBySale() {
        var sale = createSale(1, "8500");
        createPayment(sale.id());

        var first = refunds.create(
                request(
                        sale.id(),
                        null,
                        "1000",
                        RefundMethod.CASH,
                        "Первый",
                        UUID.randomUUID()
                )
        );

        var second = refunds.create(
                request(
                        sale.id(),
                        null,
                        "2000",
                        RefundMethod.TRANSFER,
                        "Второй",
                        UUID.randomUUID()
                )
        );

        var history = refunds.getBySale(sale.id());

        assertThat(history)
                .hasSize(2);

        assertThat(history)
                .extracting(value -> value.id())
                .containsExactly(
                        first.id(),
                        second.id()
                );
    }

    @Test
    void missingRefundThrowsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(
                () -> refunds.get(missing)
        )
                .isInstanceOf(
                        RefundNotFoundException.class
                );
    }

    @Test
    void paymentIsNotModifiedByRefund() {
        var sale = createSale(1, "8500");
        var payment = createPayment(sale.id());

        refunds.create(
                request(
                        sale.id(),
                        null,
                        "3000",
                        RefundMethod.CASH,
                        "Частичный возврат",
                        UUID.randomUUID()
                )
        );

        var after = payments.get(payment.id());

        assertThat(after.amount())
                .isEqualByComparingTo("8500");

        assertThat(after.status())
                .isEqualTo(
                        kg.chairx.payment.domain.PaymentStatus.PAID
                );
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

    private kg.chairx.payment.api.PaymentResponse createPayment(
            UUID saleId
    ) {
        return payments.create(
                new CreatePaymentRequest(
                        saleId,
                        PaymentMethod.TRANSFER,
                        null,
                        null
                )
        );
    }

    private kg.chairx.returning.domain.Return createReturn(
            UUID saleId,
            UUID saleItemId,
            long quantity
    ) {
        return returns.create(
                new CreateReturnRequest(
                        saleId,
                        home,
                        UUID.randomUUID(),
                        List.of(
                                new CreateReturnItemRequest(
                                        saleItemId,
                                        quantity,
                                        ReturnCondition.SELLABLE
                                )
                        ),
                        "Возврат товара",
                        null
                )
        );
    }

    private CreateRefundRequest request(
            UUID saleId,
            UUID returnId,
            String amount,
            RefundMethod method,
            String reason,
            UUID idempotencyKey
    ) {
        return new CreateRefundRequest(
                saleId,
                returnId,
                new BigDecimal(amount),
                method,
                reason,
                null,
                null,
                idempotencyKey
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
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_restorations, inventory_cost_write_offs, inventory_cost_layers, exchange_settlements, exchanges, refunds,
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

    private long refundCount(UUID saleId) {
        return jdbc.queryForObject(
                """
                select count(*)
                from refunds
                where sale_id=?
                """,
                Long.class,
                saleId
        );
    }

    private BigDecimal refundedAmount(UUID saleId) {
        return jdbc.queryForObject(
                """
                select coalesce(sum(amount), 0)
                from refunds
                where sale_id=?
                """,
                BigDecimal.class,
                saleId
        );
    }

    private long refundAuditCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from audit_entries
                where entity_type='REFUND'
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
}