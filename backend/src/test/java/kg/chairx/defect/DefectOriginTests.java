package kg.chairx.defect;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.defect.application.DefectRuleViolationException;
import kg.chairx.defect.application.DefectService;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static kg.chairx.inventory.domain.StockMovementType.ADJUSTMENT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DefectOriginTests {

    @Autowired
    DefectService defects;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    UUID warehouse;
    UUID otherWarehouse;

    UUID product;
    UUID variant;

    UUID supplier;
    UUID purchase;
    UUID purchaseItem;
    UUID receipt;
    UUID receiptItem;

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

        otherWarehouse = jdbc.queryForObject(
                "select id from warehouses where code='OFFICE'",
                UUID.class
        );

        product = UUID.randomUUID();
        variant = UUID.randomUUID();

        supplier = UUID.randomUUID();
        purchase = UUID.randomUUID();
        purchaseItem = UUID.randomUUID();
        receipt = UUID.randomUUID();
        receiptItem = UUID.randomUUID();

        jdbc.update("""
                insert into products(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Defect origin fixture', true, now(), now())
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
                values (?, ?, 'Black', 100, true, now(), now())
                """,
                variant,
                product
        );

        jdbc.update("""
                insert into suppliers(
                    id,
                    name,
                    active,
                    created_at,
                    updated_at
                )
                values (?, 'Test supplier', true, now(), now())
                """,
                supplier
        );

        jdbc.update("""
                insert into purchases(
                    id,
                    supplier_id,
                    status,
                    cargo_cost,
                    cargo_allocation_method,
                    costs_locked_at,
                    confirmed_at,
                    created_by,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    ?,
                    'RECEIVED',
                    0,
                    'BY_QUANTITY',
                    now(),
                    now(),
                    'test',
                    now(),
                    now()
                )
                """,
                purchase,
                supplier
        );

        jdbc.update("""
                insert into purchase_items(
                    id,
                    purchase_id,
                    product_variant_id,
                    line_number,
                    ordered_quantity,
                    received_quantity,
                    purchase_unit_cost,
                    allocated_cargo_cost,
                    final_unit_cost
                )
                values (
                    ?,
                    ?,
                    ?,
                    1,
                    10,
                    10,
                    100,
                    0,
                    100
                )
                """,
                purchaseItem,
                purchase,
                variant
        );

        jdbc.update("""
                insert into purchase_receipts(
                    id,
                    purchase_id,
                    warehouse_id,
                    idempotency_key,
                    request_fingerprint,
                    posted_at,
                    created_by
                )
                values (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    now(),
                    'test'
                )
                """,
                receipt,
                purchase,
                warehouse,
                UUID.randomUUID(),
                "a".repeat(64)
        );

        jdbc.update("""
                insert into purchase_receipt_items(
                    id,
                    purchase_id,
                    receipt_id,
                    purchase_item_id,
                    quantity,
                    allocated_cargo_cost,
                    total_cost
                )
                values (
                    ?,
                    ?,
                    ?,
                    ?,
                    10,
                    0,
                    1000
                )
                """,
                receiptItem,
                purchase,
                receipt,
                purchaseItem
        );

        addStock(10);
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

    @Test
    void defectCanReferenceMatchingPurchaseReceiptItem() {
        var defect = defects.open(
                warehouse,
                variant,
                supplier,
                receiptItem,
                2,
                "Повреждение обнаружено при поступлении",
                "warehouse-worker"
        );

        assertThat(defect.id()).isNotNull();
        assertThat(defect.warehouseId()).isEqualTo(warehouse);
        assertThat(defect.productVariantId()).isEqualTo(variant);
        assertThat(defect.supplierId()).isEqualTo(supplier);
        assertThat(defect.purchaseReceiptItemId())
                .isEqualTo(receiptItem);

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        assertThat(count("defects")).isEqualTo(1);
    }

    @Test
    void defectCannotReferenceReceiptItemFromAnotherWarehouse() {
        assertThatThrownBy(() -> defects.open(
                otherWarehouse,
                variant,
                supplier,
                receiptItem,
                2,
                "Неверный склад",
                "warehouse-worker"
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Позиция поступления относится к другому складу"
                );

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);
    }

    @Test
    void defectCannotReferenceReceiptItemForAnotherVariant() {
        UUID wrongVariant = UUID.randomUUID();

        assertThatThrownBy(() -> defects.open(
                warehouse,
                wrongVariant,
                supplier,
                receiptItem,
                2,
                "Неверный вариант товара",
                "warehouse-worker"
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Позиция поступления относится к другому варианту товара"
                );

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);
    }

    @Test
    void defectCannotReferenceReceiptItemFromAnotherSupplier() {
        UUID wrongSupplier = UUID.randomUUID();

        assertThatThrownBy(() -> defects.open(
                warehouse,
                variant,
                wrongSupplier,
                receiptItem,
                2,
                "Неверный поставщик",
                "warehouse-worker"
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Поставщик не соответствует позиции поступления"
                );

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);
    }

    @Test
    void openingDefectRequiresActorBeforeStockIsBlocked() {
        assertThatThrownBy(() -> defects.open(
                warehouse,
                variant,
                supplier,
                receiptItem,
                2,
                "Брак",
                "   "
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Укажите инициатора операции"
                );

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);

        /*
         * Только первоначальный ADJUSTMENT_IN.
         * Неудачная попытка создания дефекта не должна
         * создавать физических движений.
         */
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    @Test
    void resolvingDefectRequiresActorBeforeStockIsUnblocked() {
        var opened = defects.open(
                warehouse,
                variant,
                supplier,
                receiptItem,
                2,
                "Дефект для проверки actor",
                "warehouse-worker"
        );

        assertThatThrownBy(() -> defects.resolve(
                opened.id(),
                "Исправлено",
                "   "
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Укажите инициатора операции"
                );

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        String status = jdbc.queryForObject(
                """
                select status
                from defects
                where id = ?
                """,
                String.class,
                opened.id()
        );

        assertThat(status)
                .isEqualTo("OPEN");
    }

    @Test
    void writingOffDefectRequiresActorBeforeStockChanges() {
        var opened = defects.open(
                warehouse,
                variant,
                supplier,
                receiptItem,
                2,
                "Дефект для проверки списания",
                "warehouse-worker"
        );

        assertThatThrownBy(() -> defects.writeOff(
                opened.id(),
                "Списано",
                null
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Укажите инициатора операции"
                );

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        /*
         * WRITE_OFF не должен появиться.
         * Остаётся только ADJUSTMENT_IN из fixture.
         */
        assertThat(count("stock_movements")).isEqualTo(1);

        String status = jdbc.queryForObject(
                """
                select status
                from defects
                where id = ?
                """,
                String.class,
                opened.id()
        );

        assertThat(status)
                .isEqualTo("OPEN");
    }

    @Test
    void unknownReceiptItemIsRejectedBeforeStockChanges() {
        UUID unknownReceiptItem = UUID.randomUUID();

        assertThatThrownBy(() -> defects.open(
                warehouse,
                variant,
                supplier,
                unknownReceiptItem,
                2,
                "Несуществующая позиция поступления",
                "warehouse-worker"
        ))
                .isInstanceOf(
                        DefectRuleViolationException.class
                )
                .hasMessage(
                        "Позиция поступления не найдена"
                );

        assertThat(count("defects")).isZero();

        var balance = inventory.getBalance(
                warehouse,
                variant
        );

        assertThat(balance.onHand()).isEqualTo(10);
        assertThat(balance.blocked()).isZero();
        assertThat(balance.available()).isEqualTo(10);
    }

    private void addStock(long quantity) {
        inventory.recordMovement(
                new RecordStockMovement(
                        UUID.randomUUID(),
                        warehouse,
                        variant,
                        ADJUSTMENT_IN,
                        quantity,
                        "DEFECT_ORIGIN_TEST_SETUP",
                        UUID.randomUUID(),
                        "test"
                )
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
                    inventory_balances,
                    purchase_receipt_items,
                    purchase_receipts,
                    purchase_items,
                    purchases,
                    suppliers
                """);

        jdbc.update("""
                delete from audit_entries
                where entity_type in (
                    'STOCK_MOVEMENT',
                    'DEFECT'
                )
                """);
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Integer.class
        );
    }
}