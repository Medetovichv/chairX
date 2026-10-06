package kg.chairx.defect;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.defect.application.DefectRuleViolationException;
import kg.chairx.defect.application.DefectService;
import kg.chairx.defect.domain.DefectStatus;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.InsufficientStockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DefectTests {

    @Autowired
    DefectService defects;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID warehouse;
    UUID product;
    UUID variant;

    @BeforeEach
    void fixture() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");

        reset();

        warehouse = jdbc.queryForObject(
                "select id from warehouses where code='HOME'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Defect fixture', true, now(), now())
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
                values (?, ?, 'Black', 100, true, now(), now())
                """, variant, product);
    }

    @AfterEach
    void cleanup() {
        reset();

        jdbc.update(
                "delete from product_variants where id=?",
                variant
        );

        jdbc.update(
                "delete from products where id=?",
                product
        );
    }

    private void reset() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");

        jdbc.execute("""
                truncate table
                    defects,
                    stock_movements,
                    inventory_balances
                """);

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'STOCK_MOVEMENT',
                    'DEFECT'
                )
                """);
    }

    @Test
    void openingDefectBlocksAvailableStockWithoutPhysicalMovement() {
        addStock(10);

        var defect = defects.open(
                warehouse,
                variant,
                null,
                null,
                2,
                "Повреждён механизм наклона",
                "warehouse-worker"
        );

        assertThat(defect.id()).isNotNull();
        assertThat(defect.status()).isEqualTo(DefectStatus.OPEN);
        assertThat(defect.quantity()).isEqualTo(2);
        assertThat(defect.description())
                .isEqualTo("Повреждён механизм наклона");

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.reserved()).isZero();
        assertThat(balance.blocked()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        assertThat(count("defects")).isEqualTo(1);

        // Только первоначальный ADJUSTMENT_IN.
        // Блокировка брака не является физическим движением.
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void waitingForPartsKeepsStockBlocked() {
        addStock(10);

        var opened = defects.open(
                warehouse,
                variant,
                null,
                null,
                2,
                "Не работает механизм",
                "warehouse-worker"
        );

        var waiting = defects.waitForParts(opened.id());

        assertThat(waiting.status())
                .isEqualTo(DefectStatus.WAITING_PARTS);

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void resolvingDefectUnblocksStockWithoutPhysicalMovement() {
        addStock(10);

        var opened = defects.open(
                warehouse,
                variant,
                null,
                null,
                2,
                "Повреждён механизм",
                "warehouse-worker"
        );

        var resolved = defects.resolve(
                opened.id(),
                "Механизм заменён",
                "warehouse-worker"
        );

        assertThat(resolved.status())
                .isEqualTo(DefectStatus.RESOLVED);

        assertThat(resolved.resolutionNote())
                .isEqualTo("Механизм заменён");

        assertThat(resolved.resolvedBy())
                .isEqualTo("warehouse-worker");

        assertThat(resolved.resolvedAt()).isNotNull();

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.reserved()).isZero();
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);

        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void cannotOpenDefectForMoreThanAvailableStock() {
        addStock(3);

        assertThatThrownBy(() -> defects.open(
                warehouse,
                variant,
                null,
                null,
                4,
                "Брак",
                "warehouse-worker"
        ))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(3);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(3);
    }

    @Test
    void secondDefectCannotUseAlreadyBlockedStock() {
        addStock(5);

        defects.open(
                warehouse,
                variant,
                null,
                null,
                4,
                "Первый дефект",
                "warehouse-worker"
        );

        assertThatThrownBy(() -> defects.open(
                warehouse,
                variant,
                null,
                null,
                2,
                "Второй дефект",
                "warehouse-worker"
        ))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(count("defects")).isEqualTo(1);

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(5);
        assertThat(balance.blocked()).isEqualTo(4);
        assertThat(balance.available()).isEqualTo(1);
    }

    @Test
    void resolvedDefectCannotBeResolvedAgain() {
        addStock(1);

        var opened = defects.open(
                warehouse,
                variant,
                null,
                null,
                1,
                "Брак",
                "warehouse-worker"
        );

        defects.resolve(
                opened.id(),
                "Исправлено",
                "warehouse-worker"
        );

        assertThatThrownBy(() -> defects.resolve(
                opened.id(),
                "Исправлено ещё раз",
                "warehouse-worker"
        ))
                .isInstanceOf(DefectRuleViolationException.class);

        var balance = inventory.getBalance(warehouse, variant);

        assertThat(balance.onHand()).isEqualTo(1);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(1);
    }

    @Test
    void databaseFailureWhileCreatingDefectRollsBackBlockedStock() {
        addStock(5);

        jdbc.execute("""
                create function test_reject_defect()
                returns trigger
                language plpgsql
                as $$
                begin
                    raise exception 'Simulated defect failure';
                end;
                $$
                """);

        jdbc.execute("""
                create trigger test_reject_defect
                before insert on defects
                for each row
                execute function test_reject_defect()
                """);

        try {
            assertThatThrownBy(() -> defects.open(
                    warehouse,
                    variant,
                    null,
                    null,
                    2,
                    "Этот INSERT должен упасть",
                    "warehouse-worker"
            ))
                    .isInstanceOf(DataAccessException.class);

            assertThat(count("defects")).isZero();

            var balance = inventory.getBalance(
                    warehouse,
                    variant
            );

            assertThat(balance.onHand()).isEqualTo(5);
            assertThat(balance.blocked()).isZero();
            assertThat(balance.available()).isEqualTo(5);

        } finally {
            jdbc.execute("""
                    drop trigger if exists test_reject_defect
                    on defects
                    """);

            jdbc.execute("""
                    drop function if exists test_reject_defect()
                    """);
        }
    }

    private void addStock(long quantity) {
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouse,
                        variant,
                        ADJUSTMENT_IN,
                        quantity,
                        "DEFECT_TEST_SETUP",
                        UUID.randomUUID(),
                        "test"
                )
        );
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Integer.class
        );
    }
}