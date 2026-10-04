package kg.chairx.inventory;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryOperationConflictException;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.InsufficientStockException;
import kg.chairx.inventory.domain.StockMovement;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.inventory.domain.StockQuantityLimitException;
import kg.chairx.product.application.ProductVariantNotFoundException;
import kg.chairx.warehouse.application.WarehouseNotFoundException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static kg.chairx.inventory.domain.StockMovementType.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class InventoryTests {
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    UUID warehouse;
    UUID product;
    UUID variant;

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        clearInventory();
        warehouse = jdbc.queryForObject("select id from warehouses where code='HOME'", UUID.class);
        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values (?, 'Inventory fixture', true, now(), now())", product);
        jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values (?, ?, 'Black', 100, true, now(), now())
                """, variant, product);
    }

    @AfterEach
    void cleanup() {
        clearInventory();
        jdbc.update("delete from product_variants where id=?", variant);
        jdbc.update("delete from products where id=?", product);
    }

    private void clearInventory() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        // Fixture reset only: ordinary UPDATE/DELETE of the append-only journal is forbidden by a trigger.
        jdbc.execute("truncate table stock_movements, inventory_balances");
        jdbc.update("delete from audit_entries where entity_type='STOCK_MOVEMENT'");
    }

    @Test
    void absentBalanceReadsAsZeroWithoutCreatingRows() {
        var balance = inventory.getBalance(warehouse, variant);
        assertThat(balance.onHand()).isZero();
        assertThat(balance.available()).isZero();
        assertThat(count("inventory_balances")).isZero();
    }

    @ParameterizedTest
    @EnumSource(StockMovementType.class)
    void eachPhysicalMovementChangesOnlyOnHandAndRecordsItsSource(StockMovementType type) {
        inventory.recordMovement(command(ADJUSTMENT_IN, 10));
        var request = command(type, 3);
        var movement = inventory.recordMovement(request);
        var balance = inventory.getBalance(warehouse, variant);
        assertThat(balance.onHand()).isEqualTo(type.isIncoming() ? 13 : 7);
        assertThat(balance.reserved()).isZero();
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(balance.onHand());
        assertThat(movement.type()).isEqualTo(type);
        assertThat(movement.quantity()).isEqualTo(3);
        assertThat(movement.sourceId()).isEqualTo(request.sourceId());
        assertThat(movement.sourceType()).isEqualTo("TEST_OPERATION");
        assertThat(movement.actor()).isEqualTo("warehouse-worker");
        assertThat(movement.occurredAt()).isNotNull();
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void outgoingFromMissingBalanceRollsBackItsInitialRow() {
        assertThatThrownBy(() -> inventory.recordMovement(command(SALE_OUT, 1)))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(count("inventory_balances")).isZero();
        assertThat(count("stock_movements")).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void reservedAndBlockedAreNeverSpentByPhysicalOperation() {
        inventory.recordMovement(command(PURCHASE_IN, 10));
        jdbc.update("update inventory_balances set reserved=3, blocked=2 where warehouse_id=? and product_variant_id=?", warehouse, variant);
        assertThat(inventory.getBalance(warehouse, variant).available()).isEqualTo(5);
        assertThatThrownBy(() -> inventory.recordMovement(command(SALE_OUT, 6)))
                .isInstanceOfSatisfying(InsufficientStockException.class, error -> {
                    assertThat(error.getAvailable()).isEqualTo(5);
                    assertThat(error.getRequested()).isEqualTo(6);
                });
        inventory.recordMovement(command(SALE_OUT, 5));
        var result = inventory.getBalance(warehouse, variant);
        assertThat(result.onHand()).isEqualTo(5);
        assertThat(result.reserved()).isEqualTo(3);
        assertThat(result.blocked()).isEqualTo(2);
        assertThat(result.available()).isZero();
        assertThat(count("stock_movements")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void nonPositiveQuantitiesAreRejected(long quantity) {
        assertThatThrownBy(() -> inventory.recordMovement(command(ADJUSTMENT_IN, quantity)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(count("inventory_balances")).isZero();
    }

    @Test
    void malformedCommandsAreRejectedBeforeWriting() {
        assertThatThrownBy(() -> inventory.recordMovement(new RecordStockMovement(null, warehouse, variant,
                null, 1, " ", null, null))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> inventory.recordMovement(null)).isInstanceOf(ConstraintViolationException.class);
        assertThat(count("stock_movements")).isZero();
    }

    @Test
    void missingReferencesAreRejected() {
        var request = command(PURCHASE_IN, 1);
        assertThatThrownBy(() -> inventory.recordMovement(new RecordStockMovement(request.operationId(),
                UUID.randomUUID(), variant, request.type(), 1, request.sourceType(), request.sourceId(), request.actor())))
                .isInstanceOf(WarehouseNotFoundException.class);
        assertThatThrownBy(() -> inventory.recordMovement(new RecordStockMovement(request.operationId(),
                warehouse, UUID.randomUUID(), request.type(), 1, request.sourceType(), request.sourceId(), request.actor())))
                .isInstanceOf(ProductVariantNotFoundException.class);
        assertThat(count("inventory_balances")).isZero();
    }

    @Test
    void arithmeticOverflowDoesNotCorruptStock() {
        inventory.recordMovement(command(ADJUSTMENT_IN, Long.MAX_VALUE));
        assertThatThrownBy(() -> inventory.recordMovement(command(ADJUSTMENT_IN, 1)))
                .isInstanceOf(StockQuantityLimitException.class);
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isEqualTo(Long.MAX_VALUE);
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void replayOfLastUnitDispatchReturnsOriginalMovementWithoutSpendingTwice() {
        inventory.recordMovement(command(PURCHASE_IN, 1));
        var request = command(SALE_OUT, 1);
        var first = inventory.recordMovement(request);
        assertThat(inventory.recordMovement(request)).isEqualTo(first);
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isZero();
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void operationKeyCannotBeReusedWithDifferentPayload() {
        var request = command(PURCHASE_IN, 1);
        inventory.recordMovement(request);
        assertThatThrownBy(() -> inventory.recordMovement(new RecordStockMovement(request.operationId(),
                warehouse, variant, request.type(), 2, request.sourceType(), request.sourceId(), request.actor())))
                .isInstanceOf(InventoryOperationConflictException.class);
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void twoSourceLinesCanShareSourceIdWithoutBeingCollapsed() {
        UUID source = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            inventory.recordMovement(new RecordStockMovement(UUID.randomUUID(), warehouse, variant,
                    PURCHASE_IN, 1, "PURCHASE_RECEIPT", source, null));
        }
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isEqualTo(2);
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(jdbc.queryForList("select distinct actor from audit_entries where entity_type='STOCK_MOVEMENT'", String.class))
                .containsExactly("SYSTEM");
    }

    @Test
    void outerBusinessFailureRollsBackMovementBalanceAndAudit() {
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            inventory.recordMovement(command(PURCHASE_IN, 3));
            throw new IllegalStateException("Business operation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("inventory_balances")).isZero();
        assertThat(count("stock_movements")).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditFailureRollsBackInsertedMovementAndBalance() {
        jdbc.execute("""
                create function test_reject_inventory_audit() returns trigger language plpgsql as $$
                begin
                    if NEW.entity_type = 'STOCK_MOVEMENT' then raise exception 'Simulated audit failure'; end if;
                    return NEW;
                end; $$
                """);
        jdbc.execute("create trigger test_reject_inventory_audit before insert on audit_entries for each row execute function test_reject_inventory_audit()");
        try {
            assertThatThrownBy(() -> inventory.recordMovement(command(PURCHASE_IN, 3)))
                    .isInstanceOf(DataAccessException.class);
            assertThat(count("inventory_balances")).isZero();
            assertThat(count("stock_movements")).isZero();
            assertThat(auditCount()).isZero();
        } finally {
            jdbc.execute("drop trigger test_reject_inventory_audit on audit_entries");
            jdbc.execute("drop function test_reject_inventory_audit()");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"on_hand=-1", "reserved=-1", "blocked=-1", "reserved=11", "blocked=11", "reserved=6, blocked=5"})
    void postgresProtectsCountersEvenAgainstDirectSql(String assignment) {
        inventory.recordMovement(command(ADJUSTMENT_IN, 10));
        assertThatThrownBy(() -> jdbc.update("update inventory_balances set " + assignment))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inventory.getBalance(warehouse, variant).available()).isEqualTo(10);
    }

    @Test
    void databaseEnforcesOneBalanceAndReferenceIntegrity() {
        inventory.recordMovement(command(ADJUSTMENT_IN, 1));
        assertThatThrownBy(() -> jdbc.update("insert into inventory_balances(warehouse_id,product_variant_id) values (?,?)", warehouse, variant))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into inventory_balances(warehouse_id,product_variant_id) values (?,?)", UUID.randomUUID(), variant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void journalIsAppendOnlyAndReadHistoryIsPaged() {
        var movement = inventory.recordMovement(command(PURCHASE_IN, 2));
        inventory.recordMovement(command(SALE_OUT, 1));
        assertThatThrownBy(() -> jdbc.update("update stock_movements set quantity=100 where id=?", movement.id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from stock_movements where id=?", movement.id()))
                .isInstanceOf(DataAccessException.class);
        var history = inventory.listMovements(warehouse, variant, 0, 1);
        assertThat(history.items()).containsExactly(movement);
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(inventory.listMovements(warehouse, variant, 1, 1).items().getFirst().type()).isEqualTo(SALE_OUT);
        assertThatThrownBy(() -> inventory.listMovements(warehouse, variant, 0, 101))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void parallelFirstReceiptsCreateOneBalanceAndKeepBothIncrements() throws Exception {
        parallel(() -> inventory.recordMovement(command(PURCHASE_IN, 1)),
                () -> inventory.recordMovement(command(PURCHASE_IN, 1)));
        assertThat(count("inventory_balances")).isEqualTo(1);
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isEqualTo(2);
        assertThat(count("stock_movements")).isEqualTo(2);
    }

    @Test
    void parallelLastUnitOperationsCannotBothSucceed() throws Exception {
        inventory.recordMovement(command(PURCHASE_IN, 1));
        Callable<String> spend = () -> {
            try { inventory.recordMovement(command(SALE_OUT, 1)); return "SUCCESS"; }
            catch (InsufficientStockException error) { return "INSUFFICIENT"; }
        };
        assertThat(parallel(spend, spend)).containsExactlyInAnyOrder("SUCCESS", "INSUFFICIENT");
        assertThat(inventory.getBalance(warehouse, variant).available()).isZero();
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void parallelIdenticalRetriesProduceOnePhysicalChange() throws Exception {
        var request = command(PURCHASE_IN, 5);
        List<StockMovement> results = parallel(() -> inventory.recordMovement(request), () -> inventory.recordMovement(request));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(inventory.getBalance(warehouse, variant).onHand()).isEqualTo(5);
        assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void parallelSameKeyOnDifferentWarehousesDoesNotLeaveAnExtraBalance() throws Exception {
        UUID office = jdbc.queryForObject("select id from warehouses where code='OFFICE'", UUID.class);
        var first = command(PURCHASE_IN, 1);
        var second = new RecordStockMovement(first.operationId(), office, variant, first.type(), first.quantity(),
                first.sourceType(), first.sourceId(), first.actor());
        Callable<String> homeOperation = () -> applyOrConflict(first);
        Callable<String> officeOperation = () -> applyOrConflict(second);
        assertThat(parallel(homeOperation, officeOperation)).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertThat(count("inventory_balances")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void v3UpgradesExistingV2WithoutChangingWarehousesOrProducts() {
        String schema = "inventory_upgrade_test";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
            jdbc.update("update inventory_upgrade_test.warehouses set name='Сохранить' where code='HOME'");
            UUID id = UUID.randomUUID();
            jdbc.update("insert into inventory_upgrade_test.products(id,name,active,created_at,updated_at) values (?, 'Existing', true, now(), now())", id);
            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("select name from inventory_upgrade_test.warehouses where code='HOME'", String.class)).isEqualTo("Сохранить");
            assertThat(jdbc.queryForObject("select name from inventory_upgrade_test.products where id=?", String.class, id)).isEqualTo("Existing");
            assertThat(jdbc.queryForObject("select count(*) from inventory_upgrade_test.inventory_balances", Integer.class)).isZero();
        } finally {
            jdbc.execute("drop schema if exists inventory_upgrade_test cascade");
        }
    }

    private String applyOrConflict(RecordStockMovement command) {
        try { inventory.recordMovement(command); return "SUCCESS"; }
        catch (InventoryOperationConflictException error) { return "CONFLICT"; }
    }

    private <T> List<T> parallel(Callable<T> first, Callable<T> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return first.call(); });
            var right = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return second.call(); });
            return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        }
    }

    private RecordStockMovement command(StockMovementType type, long quantity) {
        return new RecordStockMovement(UUID.randomUUID(), warehouse, variant, type, quantity,
                "TEST_OPERATION", UUID.randomUUID(), "warehouse-worker");
    }

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private int auditCount() { return jdbc.queryForObject("select count(*) from audit_entries where entity_type='STOCK_MOVEMENT'", Integer.class); }
}
