package kg.chairx.returning;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.application.ReturnRuleViolationException;
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
class ReturnConcurrencyTests {

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
        authenticate("return-concurrency-setup");

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
                values (?, 'Return concurrency fixture', true, now(), now())
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
                values (?, ?, 'Black', 10000, true, now(), now())
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
                    'Return Concurrency Customer',
                    '+996555000001',
                    true,
                    now(),
                    now()
                )
                """, customer);

        addStock(10);
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
    void concurrentReturnsCannotReturnSameSoldUnitTwice()
            throws Exception {

        var sale = sales.create(
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

        sale = sales.fulfill(sale.id());

        UUID saleId = sale.id();
        UUID saleItemId = sale.items().getFirst().id();

        /*
         * Initial stock = 10.
         * Fulfillment physically removes one unit.
         */
        assertThat(
                inventory.getBalance(
                        home,
                        variant
                ).onHand()
        ).isEqualTo(9);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            Future<Result> first = executor.submit(
                    () -> executeReturn(
                            saleId,
                            saleItemId,
                            UUID.randomUUID(),
                            ready,
                            start,
                            "return-concurrency-1"
                    )
            );

            Future<Result> second = executor.submit(
                    () -> executeReturn(
                            saleId,
                            saleItemId,
                            UUID.randomUUID(),
                            ready,
                            start,
                            "return-concurrency-2"
                    )
            );

            /*
             * Make sure both worker threads are ready before
             * releasing them at approximately the same time.
             */
            assertThat(
                    ready.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            start.countDown();

            Result firstResult =
                    first.get(20, TimeUnit.SECONDS);

            Result secondResult =
                    second.get(20, TimeUnit.SECONDS);

            long successCount =
                    List.of(
                                    firstResult,
                                    secondResult
                            )
                            .stream()
                            .filter(Result::success)
                            .count();

            long rejectedCount =
                    List.of(
                                    firstResult,
                                    secondResult
                            )
                            .stream()
                            .filter(result ->
                                    !result.success()
                                            && "RETURN_QUANTITY_EXCEEDED"
                                            .equals(result.errorCode())
                            )
                            .count();

            assertThat(successCount)
                    .isEqualTo(1);

            assertThat(rejectedCount)
                    .isEqualTo(1);
        }

        /*
         * Only one Return must exist.
         */
        assertThat(count("returns"))
                .isEqualTo(1);

        assertThat(count("return_items"))
                .isEqualTo(1);

        /*
         * Only the one sold unit may have been returned.
         */
        Long returnedQuantity =
                jdbc.queryForObject("""
                        select coalesce(sum(quantity), 0)
                        from return_items
                        where sale_item_id=?
                        """,
                        Long.class,
                        saleItemId
                );

        assertThat(returnedQuantity)
                .isEqualTo(1);

        /*
         * Exactly one physical RETURN_IN.
         */
        Long returnMovements =
                jdbc.queryForObject("""
                        select count(*)
                        from stock_movements
                        where movement_type='RETURN_IN'
                          and source_type='SALE_RETURN'
                        """,
                        Long.class
                );

        assertThat(returnMovements)
                .isEqualTo(1);

        /*
         * Stock:
         *
         * 10 initial
         * -1 SALE_OUT
         * +1 RETURN_IN
         * =10
         */
        var balance = inventory.getBalance(
                home,
                variant
        );

        assertThat(balance.onHand())
                .isEqualTo(10);

        assertThat(balance.reserved())
                .isZero();

        assertThat(balance.blocked())
                .isZero();

        assertThat(balance.available())
                .isEqualTo(10);
    }

    private Result executeReturn(
            UUID saleId,
            UUID saleItemId,
            UUID idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start,
            String actor
    ) {
        authenticate(actor);

        ready.countDown();

        try {
            if (!start.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                return Result.failure(
                        "START_TIMEOUT"
                );
            }

            returns.create(
                    new CreateReturnRequest(
                            saleId,
                            home,
                            idempotencyKey,
                            List.of(
                                    new CreateReturnItemRequest(
                                            saleItemId,
                                            1,
                                            ReturnCondition.SELLABLE
                                    )
                            ),
                            "Параллельный возврат",
                            "Concurrency integration test"
                    )
            );

            return Result.successResult();

        } catch (ReturnRuleViolationException exception) {
            return Result.failure(
                    exception.getCode()
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return Result.failure(
                    "INTERRUPTED"
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        username,
                        null,
                        List.of()
                )
        );
    }

    private void addStock(long quantity) {
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        ADJUSTMENT_IN,
                        quantity,
                        "RETURN_CONCURRENCY_FIXTURE",
                        UUID.randomUUID(),
                        "return-concurrency-setup"
                )
        );
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Long.class
        );
    }

    private void clearData() {
        assertTestDatabase();

        jdbc.execute("""
                truncate table
                    inventory_cost_movements, inventory_cost_allocations, inventory_cost_layers, exchange_settlements, exchanges, refunds,
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
                    'SALE',
                    'RETURN',
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

    private record Result(
            boolean success,
            String errorCode
    ) {

        static Result successResult() {
            return new Result(
                    true,
                    null
            );
        }

        static Result failure(String errorCode) {
            return new Result(
                    false,
                    errorCode
            );
        }
    }
}