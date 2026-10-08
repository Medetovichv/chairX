package kg.chairx.delivery;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.application.DeliveryRuleViolationException;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
class DeliveryConcurrencyTests {

    @Autowired
    DeliveryService deliveries;

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
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "delivery-concurrency-test-user",
                        null,
                        List.of()
                )
        );

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
                values (?, 'Delivery concurrency fixture', true, now(), now())
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
                values (?, ?, 'Black', 10000, true, now(), now())
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
                values (?, 'Concurrency Customer', '+996555000002', true, now(), now())
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
                        "DELIVERY_CONCURRENCY_FIXTURE",
                        UUID.randomUUID(),
                        "delivery-concurrency-test"
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
    void concurrentCreateAllowsExactlyOneDeliveryForSale()
            throws Exception {

        var sale = sales.create(
                new CreateSaleRequest(
                        UUID.randomUUID(),
                        customer,
                        FulfillmentType.CITY_DELIVERY,
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

        var request = new CreateDeliveryRequest(
                sale.id(),
                "Иван Иванов",
                "+996555111222",
                "Бишкек, ул. Тестовая 10",
                "Бишкек",
                new BigDecimal("300"),
                "ChairX Courier",
                null,
                "Concurrency test"
        );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            Callable<Object> task = () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "concurrent-delivery-test-user",
                                null,
                                List.of()
                        )
                );

                try {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent delivery test did not start in time"
                        );
                    }

                    return deliveries.create(request);

                } catch (DeliveryRuleViolationException exception) {
                    return exception;

                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            Future<Object> first =
                    executor.submit(task);

            Future<Object> second =
                    executor.submit(task);

            assertThat(
                    ready.await(5, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            Object firstResult =
                    get(first);

            Object secondResult =
                    get(second);

            long successCount = List.of(
                            firstResult,
                            secondResult
                    )
                    .stream()
                    .filter(result ->
                            result instanceof kg.chairx.delivery.api.DeliveryResponse
                    )
                    .count();

            long conflictCount = List.of(
                            firstResult,
                            secondResult
                    )
                    .stream()
                    .filter(result ->
                            result instanceof DeliveryRuleViolationException
                    )
                    .count();

            assertThat(successCount)
                    .isEqualTo(1);

            assertThat(conflictCount)
                    .isEqualTo(1);

            DeliveryRuleViolationException conflict =
                    (DeliveryRuleViolationException)
                            List.of(firstResult, secondResult)
                                    .stream()
                                    .filter(result ->
                                            result instanceof DeliveryRuleViolationException
                                    )
                                    .findFirst()
                                    .orElseThrow();

            assertThat(conflict.code())
                    .isEqualTo("DELIVERY_ALREADY_EXISTS");

            assertThat(
                    jdbc.queryForObject(
                            """
                            select count(*)
                            from deliveries
                            where sale_id=?
                            """,
                            Long.class,
                            sale.id()
                    )
            ).isEqualTo(1);

            UUID deliveryId =
                    jdbc.queryForObject(
                            """
                            select id
                            from deliveries
                            where sale_id=?
                            """,
                            UUID.class,
                            sale.id()
                    );

            var stored =
                    deliveries.get(deliveryId);

            assertThat(stored.status())
                    .isEqualTo(DeliveryStatus.READY);

            assertThat(stored.saleId())
                    .isEqualTo(sale.id());

            /*
             * Создание Delivery само по себе не должно
             * физически двигать товар.
             */
            var balance =
                    inventory.getBalance(
                            home,
                            variant
                    );

            assertThat(balance.onHand())
                    .isEqualTo(20);

            assertThat(balance.reserved())
                    .isEqualTo(2);

            assertThat(balance.available())
                    .isEqualTo(18);

            assertThat(saleOutCount())
                    .isZero();
        }
    }

    private Object get(Future<Object> future)
            throws Exception {

        try {
            return future.get(
                    10,
                    TimeUnit.SECONDS
            );

        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof Exception actual) {
                throw actual;
            }

            throw exception;
        }
    }

    private long saleOutCount() {
        return jdbc.queryForObject(
                """
                select count(*)
                from stock_movements
                where movement_type='SALE_OUT'
                """,
                Long.class
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    exchange_settlements, exchanges, refunds,
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
                    'SALE',
                    'DELIVERY',
                    'STOCK_MOVEMENT'
                )
                """);
    }

    private void assertTestDatabase() {
        String database = jdbc.queryForObject(
                "select current_database()",
                String.class
        );

        assertThat(database)
                .isEqualTo("chairx_test");
    }
}