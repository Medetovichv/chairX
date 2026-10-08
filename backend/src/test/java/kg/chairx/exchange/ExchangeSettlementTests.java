package kg.chairx.exchange;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.exchange.application.ExchangeSettlementService;
import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.payment.domain.PaymentMethod;
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
import java.time.Instant;
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
class ExchangeSettlementTests {

    @Autowired
    ExchangeSettlementService service;

    @Autowired
    ExchangeRepository exchanges;

    @Autowired
    ExchangeSettlementRepository settlements;

    @Autowired
    SaleService sales;

    @Autowired
    ReturnService returns;

    @Autowired
    PaymentService payments;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID warehouseId;
    UUID productId;
    UUID variantId;
    UUID customerId;

    @BeforeEach
    void setup() {
        authenticate();

        assertTestDatabase();

        kg.chairx.TestDatabaseCleaner.clean(jdbc);

        warehouseId = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code = 'HOME'",
                UUID.class
        );

        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products (
                    id, name, active, created_at, updated_at
                )
                VALUES (?, 'Exchange test product', true, now(), now())
                """, productId);

        jdbc.update("""
                INSERT INTO product_variants (
                    id, product_id, name,
                    recommended_sale_price,
                    active, created_at, updated_at
                )
                VALUES (?, ?, 'Black', 8500, true, now(), now())
                """, variantId, productId);

        jdbc.update("""
                INSERT INTO customers (
                    id, full_name, phone,
                    active, created_at, updated_at
                )
                VALUES (?, 'Exchange Customer', '+996555111111',
                        true, now(), now())
                """, customerId);

        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouseId,
                        variantId,
                        ADJUSTMENT_IN,
                        30,
                        "EXCHANGE_TEST_FIXTURE",
                        UUID.randomUUID(),
                        "exchange-test"
                )
        );
    }

    @AfterEach
    void cleanup() {
        assertTestDatabase();

        kg.chairx.TestDatabaseCleaner.clean(jdbc);

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
    void fullAdditionalPaymentCompletesExchange() {
        Exchange exchange = createExchange("8500", "10000");

        var settlement = settle(
                exchange.id(),
                "IN",
                "CASH",
                "1500",
                UUID.randomUUID()
        );

        assertThat(settlement.amount())
                .isEqualByComparingTo("1500");

        assertThat(settlement.direction())
                .isEqualTo("IN");

        assertThat(settlements.total(exchange.id(), "IN"))
                .isEqualByComparingTo("1500");

        assertThat(exchanges.find(exchange.id()).orElseThrow().status())
                .isEqualTo(ExchangeStatus.COMPLETED);
    }

    @Test
    void partialPaymentsCompleteExchangeOnlyAfterFullAmount() {
        Exchange exchange = createExchange("8500", "10000");

        settle(
                exchange.id(),
                "IN",
                "CASH",
                "1000",
                UUID.randomUUID()
        );

        assertThat(exchanges.find(exchange.id()).orElseThrow().status())
                .isEqualTo(ExchangeStatus.PENDING_SETTLEMENT);

        settle(
                exchange.id(),
                "IN",
                "TRANSFER",
                "500",
                UUID.randomUUID()
        );

        assertThat(settlements.total(exchange.id(), "IN"))
                .isEqualByComparingTo("1500");

        assertThat(settlements.findByExchange(exchange.id()))
                .hasSize(2);

        assertThat(exchanges.find(exchange.id()).orElseThrow().status())
                .isEqualTo(ExchangeStatus.COMPLETED);
    }

    @Test
    void overpaymentIsRejected() {
        Exchange exchange = createExchange("8500", "10000");

        assertThatThrownBy(() -> settle(
                exchange.id(),
                "IN",
                "CASH",
                "2000",
                UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(settlements.findByExchange(exchange.id()))
                .isEmpty();

        assertThat(exchanges.find(exchange.id()).orElseThrow().status())
                .isEqualTo(ExchangeStatus.PENDING_SETTLEMENT);
    }

    @Test
    void sameIdempotencyKeyReturnsExistingSettlement() {
        Exchange exchange = createExchange("8500", "10000");

        UUID key = UUID.randomUUID();

        var first = settle(
                exchange.id(),
                "IN",
                "CASH",
                "1500",
                key
        );

        var second = settle(
                exchange.id(),
                "IN",
                "CASH",
                "1500",
                key
        );

        assertThat(second.id()).isEqualTo(first.id());

        assertThat(settlements.findByExchange(exchange.id()))
                .hasSize(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentAmountIsRejected() {
        Exchange exchange = createExchange("8500", "10000");

        UUID key = UUID.randomUUID();

        settle(
                exchange.id(),
                "IN",
                "CASH",
                "1000",
                key
        );

        assertThatThrownBy(() -> settle(
                exchange.id(),
                "IN",
                "CASH",
                "500",
                key
        )).isInstanceOf(IllegalStateException.class);

        assertThat(settlements.total(exchange.id(), "IN"))
                .isEqualByComparingTo("1000");
    }

    @Test
    void cheaperExchangeRecordsActualRefund() {
        Exchange exchange = createExchange("10000", "8500");

        var settlement = settle(
                exchange.id(),
                "OUT",
                "TRANSFER",
                "1500",
                UUID.randomUUID()
        );

        assertThat(settlement.direction())
                .isEqualTo("OUT");

        assertThat(settlement.amount())
                .isEqualByComparingTo("1500");

        assertThat(settlements.total(exchange.id(), "OUT"))
                .isEqualByComparingTo("1500");

        assertThat(exchanges.find(exchange.id()).orElseThrow().status())
                .isEqualTo(ExchangeStatus.COMPLETED);
    }

    private Exchange createExchange(
            String oldPrice,
            String newPrice
    ) {
        var originalSale = createSale(oldPrice);

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
                        "Exchange test",
                        null
                )
        );

        var newSale = createSale(newPrice);

        BigDecimal returnedValue = new BigDecimal(oldPrice);
        BigDecimal newSaleTotal = new BigDecimal(newPrice);

        BigDecimal credit = returnedValue.min(newSaleTotal);

        Exchange exchange = new Exchange(
                UUID.randomUUID(),
                originalSale.id(),
                newSale.id(),
                returned.id(),
                ExchangeStatus.PENDING_SETTLEMENT,
                returnedValue,
                newSaleTotal,
                credit,
                newSaleTotal.subtract(credit),
                returnedValue.subtract(credit),
                UUID.randomUUID(),
                "a".repeat(64),
                "exchange-test-user",
                Instant.now(),
                null,
                null
        );

        boolean inserted = exchanges.tryInsert(exchange);

        assertThat(inserted).isTrue();

        return exchange;
    }

    private kg.chairx.sale.api.SaleResponse createSale(String price) {
        return sales.create(
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
    }

    private ExchangeSettlementRepository.Settlement settle(
            UUID exchangeId,
            String direction,
            String method,
            String amount,
            UUID key
    ) {
        return service.settle(
                exchangeId,
                key,
                direction,
                method,
                new BigDecimal(amount),
                null,
                "exchange-test-user"
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
        assertThat(
                jdbc.queryForObject(
                        "SELECT current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }
}