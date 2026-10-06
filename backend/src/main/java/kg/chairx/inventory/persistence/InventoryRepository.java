package kg.chairx.inventory.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.domain.InventoryBalance;
import kg.chairx.inventory.domain.StockMovement;
import kg.chairx.inventory.domain.StockMovementType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryRepository {
    private final JdbcClient jdbc;

    public InventoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public InventoryBalance lockOrCreate(UUID warehouseId, UUID variantId) {
        // SELECT FOR UPDATE alone cannot lock a missing row. The PK also serializes its first insertion.
        jdbc.sql("""
                INSERT INTO inventory_balances (warehouse_id, product_variant_id)
                VALUES (:warehouse, :variant) ON CONFLICT (warehouse_id, product_variant_id) DO NOTHING
                """).param("warehouse", warehouseId).param("variant", variantId).update();
        return jdbc.sql("""
                SELECT warehouse_id, product_variant_id, on_hand, reserved, blocked
                FROM inventory_balances WHERE warehouse_id = :warehouse AND product_variant_id = :variant
                FOR UPDATE
                """).param("warehouse", warehouseId).param("variant", variantId)
                .query(InventoryRepository::balance).single();
    }

    public Optional<InventoryBalance> findBalance(UUID warehouseId, UUID variantId) {
        return jdbc.sql("""
                SELECT warehouse_id, product_variant_id, on_hand, reserved, blocked
                FROM inventory_balances WHERE warehouse_id = :warehouse AND product_variant_id = :variant
                """).param("warehouse", warehouseId).param("variant", variantId)
                .query(InventoryRepository::balance).optional();
    }

    public void updateOnHand(InventoryBalance balance) {
        jdbc.sql("""
                UPDATE inventory_balances SET on_hand = :quantity, updated_at = clock_timestamp()
                WHERE warehouse_id = :warehouse AND product_variant_id = :variant
                """).param("quantity", balance.onHand()).param("warehouse", balance.warehouseId())
                .param("variant", balance.productVariantId()).update();
    }

    public void updateBlocked(InventoryBalance balance) {
        jdbc.sql("""
            UPDATE inventory_balances
            SET blocked = :blocked,
                updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouse
              AND product_variant_id = :variant
            """)
                .param("blocked", balance.blocked())
                .param("warehouse", balance.warehouseId())
                .param("variant", balance.productVariantId())
                .update();
    }

    public Optional<StockMovement> findMovement(UUID operationId) {
        return jdbc.sql("SELECT * FROM stock_movements WHERE operation_id = :operation")
                .param("operation", operationId).query(InventoryRepository::movement).optional();
    }

    public Optional<StockMovement> insertMovement(RecordStockMovement command) {
        return jdbc.sql("""
                INSERT INTO stock_movements (id, operation_id, warehouse_id, product_variant_id,
                    movement_type, quantity, source_type, source_id, actor, occurred_at)
                VALUES (:id, :operation, :warehouse, :variant, :type, :quantity, :sourceType, :sourceId,
                    :actor, clock_timestamp())
                ON CONFLICT (operation_id) DO NOTHING RETURNING *
                """).param("id", UUID.randomUUID()).param("operation", command.operationId())
                .param("warehouse", command.warehouseId()).param("variant", command.productVariantId())
                .param("type", command.type().name()).param("quantity", command.quantity())
                .param("sourceType", command.sourceType()).param("sourceId", command.sourceId())
                .param("actor", command.actor(), java.sql.Types.VARCHAR)
                .query(InventoryRepository::movement).optional();
    }

    public List<StockMovement> listMovements(UUID warehouseId, UUID variantId, int page, int size) {
        return jdbc.sql("""
                SELECT * FROM stock_movements WHERE warehouse_id = :warehouse AND product_variant_id = :variant
                ORDER BY occurred_at, id LIMIT :size OFFSET :offset
                """).param("warehouse", warehouseId).param("variant", variantId)
                .param("size", size).param("offset", (long) page * size)
                .query(InventoryRepository::movement).list();
    }

    public long countMovements(UUID warehouseId, UUID variantId) {
        return jdbc.sql("""
                SELECT count(*) FROM stock_movements
                WHERE warehouse_id = :warehouse AND product_variant_id = :variant
                """).param("warehouse", warehouseId).param("variant", variantId).query(Long.class).single();
    }

    private static InventoryBalance balance(ResultSet row, int index) throws SQLException {
        return new InventoryBalance(row.getObject("warehouse_id", UUID.class),
                row.getObject("product_variant_id", UUID.class), row.getLong("on_hand"),
                row.getLong("reserved"), row.getLong("blocked"));
    }

    private static StockMovement movement(ResultSet row, int index) throws SQLException {
        return new StockMovement(row.getObject("id", UUID.class), row.getObject("operation_id", UUID.class),
                row.getObject("warehouse_id", UUID.class), row.getObject("product_variant_id", UUID.class),
                StockMovementType.valueOf(row.getString("movement_type")), row.getLong("quantity"),
                row.getString("source_type"), row.getObject("source_id", UUID.class), row.getString("actor"),
                row.getTimestamp("occurred_at").toInstant());
    }
}
