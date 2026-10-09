package kg.chairx.inventory.application;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.cost.InventoryCostException;
import kg.chairx.inventory.domain.InsufficientStockException;
import kg.chairx.inventory.domain.StockMovementType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@WithMockUser(username = "transfer-test")
@Timeout(30)
class InventoryTransferTests {

    @Autowired InventoryTransferService transfers;
    @Autowired kg.chairx.sale.application.SaleService sales;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;

    UUID home;
    UUID office;
    UUID product;
    UUID variant;

    @BeforeEach
    void setup() {
        clear();

        home = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code = 'HOME'",
                UUID.class
        );

        office = jdbc.queryForObject(
                "SELECT id FROM warehouses WHERE code = 'OFFICE'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products(
                    id, name, active, created_at, updated_at
                )
                VALUES (?, 'Transfer test', true, now(), now())
                """, product);

        jdbc.update("""
                INSERT INTO product_variants(
                    id, product_id, name,
                    recommended_sale_price, active,
                    created_at, updated_at
                )
                VALUES (?, ?, 'Transfer variant',
                        10000, true, now(), now())
                """, variant, product);
    }

    @AfterEach
    void cleanup() {
        clear();

        jdbc.update(
                "DELETE FROM product_variants WHERE id = ?",
                variant
        );

        jdbc.update(
                "DELETE FROM products WHERE id = ?",
                product
        );
    }

    void clear() {
        assertThat(jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        )).isEqualTo("chairx_test");

        jdbc.execute("""
                TRUNCATE TABLE
                    inventory_transfer_cost_origins,
                    inventory_transfers,
                    inventory_cost_restorations,
                    inventory_cost_write_offs,
                    inventory_cost_allocations,
                    inventory_cost_movements,
                    inventory_cost_layers,
                    exchange_settlements,
                    exchanges,
                    refunds,
                    return_items,
                    returns,
                    payments,
                    deliveries,
                    sale_items,
                    sales,
                    defects,
                    purchase_receipt_items,
                    purchase_receipts,
                    purchase_items,
                    purchases,
                    stock_movements,
                    inventory_balances
                """);

        jdbc.update("DELETE FROM audit_entries");
    }

    void stock(long quantity, String totalCost) {
        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                home,
                variant,
                quantity,
                new BigDecimal(totalCost),
                "transfer-test"
        );
    }

    InventoryTransferService.TransferResult transfer(
            UUID transferId,
            long quantity
    ) {
        return transfers.transfer(
                transferId,
                home,
                office,
                variant,
                quantity,
                "transfer-test"
        );
    }

    long count(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table,
                Long.class
        );
    }

    @Test
    void transfersStockBetweenWarehouses() {
        stock(5, "5000.00");

        var result = transfer(UUID.randomUUID(), 3);

        assertThat(result.quantity()).isEqualTo(3);
        assertThat(result.totalCost())
                .isEqualByComparingTo("3000.00");

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(2);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(3);

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);

        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                FROM stock_movements
                WHERE movement_type IN ('TRANSFER_OUT', 'TRANSFER_IN')
                """, Long.class)).isEqualTo(2);
    }

    @Test
    void preservesFifoCostAndOrigins() {
        stock(2, "200.00");
        stock(3, "600.00");

        var result = transfer(UUID.randomUUID(), 4);

        // FIFO: 2 x 100 + 2 x 200 = 600
        assertThat(result.totalCost())
                .isEqualByComparingTo("600.00");

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(1);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(4);

        assertThat(jdbc.queryForObject("""
                SELECT coalesce(sum(quantity), 0)
                FROM inventory_transfer_cost_origins
                """, Long.class)).isEqualTo(4);

        assertThat(jdbc.queryForObject("""
                SELECT coalesce(sum(amount), 0)
                FROM inventory_transfer_cost_origins
                """, BigDecimal.class))
                .isEqualByComparingTo("600.00");

        assertThat(jdbc.queryForObject("""
                SELECT total_cost
                FROM inventory_cost_layers
                WHERE source_movement_id = ?
                """, BigDecimal.class, result.inMovementId()))
                .isEqualByComparingTo("600.00");

        assertThat(count("inventory_transfer_cost_origins"))
                .isEqualTo(2);
    }

    @Test
    void repeatedTransferDoesNotMoveStockTwice() {
        stock(5, "500.00");

        UUID transferId = UUID.randomUUID();

        var first = transfer(transferId, 3);
        var second = transfer(transferId, 3);

        assertThat(second).isEqualTo(first);

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(2);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(3);

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);

        assertThatThrownBy(() -> transfer(transferId, 4))
                .isInstanceOf(InventoryTransferConflictException.class)
                .hasMessageContaining("TRANSFER_OPERATION_CONFLICT");

        assertThat(count("inventory_transfers")).isEqualTo(1);
    }

    @Test
    void insufficientStockDoesNotCreateTransfer() {
        stock(2, "200.00");

        assertThatThrownBy(
                () -> transfer(UUID.randomUUID(), 3)
        ).isInstanceOf(InsufficientStockException.class);

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(2);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isZero();

        assertThat(count("inventory_transfers")).isZero();
        assertThat(count("inventory_transfer_cost_origins")).isZero();
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void missingFifoValuationRollsBackPhysicalTransfer() {
        // Физический остаток есть, но FIFO-партия отсутствует.
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        home,
                        variant,
                        StockMovementType.ADJUSTMENT_IN,
                        2,
                        "LEGACY",
                        UUID.randomUUID(),
                        "transfer-test"
                )
        );

        long auditBefore = count("audit_entries");

        assertThatThrownBy(
                () -> transfer(UUID.randomUUID(), 1)
        ).isInstanceOf(InventoryCostException.class);

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(2);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isZero();

        assertThat(count("inventory_transfers")).isZero();
        assertThat(count("inventory_transfer_cost_origins")).isZero();
        assertThat(count("inventory_cost_allocations")).isZero();
        assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(count("audit_entries")).isEqualTo(auditBefore);
    }

    @Test
    void parallelSameTransferIdMovesStockOnce() throws Exception {
        stock(5, "500.00");

        UUID id = UUID.randomUUID();

        var results = parallel(
                () -> transfer(id, 3),
                () -> transfer(id, 3)
        );

        assertThat(results.get(0)).isEqualTo(results.get(1));

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(2);
        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(3);

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);
    }

    @Test
    void parallelOppositeTransfersCompleteWithoutDeadlock() throws Exception {
        stock(5, "500.00");

        adjustments.recordValuedAdjustmentIn(
                UUID.randomUUID(),
                office,
                variant,
                4,
                new BigDecimal("800.00"),
                "transfer-test"
        );

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        var results = parallel(
                () -> transfers.transfer(
                        firstId, home, office, variant, 2, "transfer-test"
                ),
                () -> transfers.transfer(
                        secondId, office, home, variant, 3, "transfer-test"
                )
        );

        assertThat(results).hasSize(2);

        // HOME: 5 - 2 + 3 = 6
        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(6);

        // OFFICE: 4 - 3 + 2 = 3
        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(3);

        assertThat(count("inventory_transfers")).isEqualTo(2);
        assertThat(count("stock_movements")).isEqualTo(6);
    }

    @Test
    void parallelTransfersCannotOversellStock() throws Exception {
        stock(5, "500.00");

        var results = parallel(
                () -> attemptTransfer(UUID.randomUUID(), 4),
                () -> attemptTransfer(UUID.randomUUID(), 4)
        );

        assertThat(results).containsExactlyInAnyOrder(true, false);

        assertThat(inventory.getBalance(home, variant).onHand())
                .isEqualTo(1);

        assertThat(inventory.getBalance(office, variant).onHand())
                .isEqualTo(4);

        assertThat(count("inventory_transfers")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
        assertThat(count("inventory_cost_allocations")).isEqualTo(1);
    }


    @Test
    void concurrentSaleAndTransferCannotClaimTheSameStock() throws Exception {
        stock(5, "500.00");

        var outcomes = parallel(
                () -> {
                    try {
                        sales.create(new kg.chairx.sale.api.CreateSaleRequest(
                                UUID.randomUUID(),
                                null,
                                kg.chairx.sale.domain.FulfillmentType.SELF_PICKUP,
                                List.of(new kg.chairx.sale.api.CreateSaleItemRequest(
                                        variant, home, 4, new BigDecimal("8500")))));
                        return "SALE";
                    } catch (InsufficientStockException expected) {
                        return "SALE_REJECTED";
                    }
                },
                () -> {
                    try {
                        transfer(UUID.randomUUID(), 4);
                        return "TRANSFER";
                    } catch (InsufficientStockException expected) {
                        return "TRANSFER_REJECTED";
                    }
                }
        );

        assertThat(outcomes).satisfiesAnyOf(
                values -> assertThat(values).containsExactlyInAnyOrder("SALE", "TRANSFER_REJECTED"),
                values -> assertThat(values).containsExactlyInAnyOrder("SALE_REJECTED", "TRANSFER")
        );

        var source = inventory.getBalance(home, variant);
        var destination = inventory.getBalance(office, variant);
        assertThat(source.available()).isEqualTo(1);

        if (outcomes.contains("SALE")) {
            assertThat(source.onHand()).isEqualTo(5);
            assertThat(source.reserved()).isEqualTo(4);
            assertThat(destination.onHand()).isZero();
            assertThat(count("sales")).isEqualTo(1);
            assertThat(count("inventory_transfers")).isZero();
        } else {
            assertThat(source.onHand()).isEqualTo(1);
            assertThat(source.reserved()).isZero();
            assertThat(destination.onHand()).isEqualTo(4);
            assertThat(count("sales")).isZero();
            assertThat(count("inventory_transfers")).isEqualTo(1);
        }

        assertThat(jdbc.queryForObject(
                "SELECT coalesce(sum(remaining_cost),0) FROM inventory_cost_layers",
                BigDecimal.class)).isEqualByComparingTo("500.00");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_balances WHERE reserved < 0 OR on_hand < reserved",
                Long.class)).isZero();
    }

    boolean attemptTransfer(UUID id, long quantity) {
        try {
            transfer(id, quantity);
            return true;
        } catch (InsufficientStockException e) {
            return false;
        }
    }

    <T> List<T> parallel(
            Callable<T> left,
            Callable<T> right
    ) throws Exception {

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);

        Callable<T> first = () -> {
            authenticate();
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return left.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        Callable<T> second = () -> {
            authenticate();
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return right.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        var a = executor.submit(first);
        var b = executor.submit(second);

        try {
            return List.of(
                    a.get(20, TimeUnit.SECONDS),
                    b.get(20, TimeUnit.SECONDS)
            );
        } finally {
            a.cancel(true);
            b.cancel(true);
            executor.shutdownNow();
        }
    }

    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "transfer-test",
                        null,
                        List.of()
                )
        );
    }

}
