package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryCostRepository {

    private final JdbcClient jdbc;

    public InventoryCostRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void consumeLayer(
            UUID layerId,
            long quantity,
            BigDecimal amount
    ) {
        int updated = jdbc.sql("""
            UPDATE inventory_cost_layers
            SET quantity_remaining = quantity_remaining - :quantity,
                remaining_cost = remaining_cost - :amount
            WHERE id = :layerId
              AND quantity_remaining >= :quantity
              AND remaining_cost >= :amount
            """)
                .param("layerId", layerId)
                .param("quantity", quantity)
                .param("amount", amount)
                .update();

        if (updated != 1) {
            throw new IllegalStateException(
                    "FIFO layer has insufficient quantity or cost"
            );
        }
    }

    public void recordConsumption(
            UUID movementId,
            UUID layerId,
            long quantity,
            BigDecimal amount
    ) {
        jdbc.sql("""
            INSERT INTO inventory_cost_allocations (
                id,
                stock_movement_id,
                cost_layer_id,
                quantity,
                allocated_cost
            )
            VALUES (
                :id,
                :movementId,
                :layerId,
                :quantity,
                :amount
            )
            """)
                .param("id", UUID.randomUUID())
                .param("movementId", movementId)
                .param("layerId", layerId)
                .param("quantity", quantity)
                .param("amount", amount)
                .update();

        jdbc.sql("""
            INSERT INTO inventory_cost_movements (
                id,
                cost_layer_id,
                stock_movement_id,
                direction,
                quantity,
                amount
            )
            VALUES (
                :id,
                :layerId,
                :movementId,
                'OUT',
                :quantity,
                :amount
            )
            """)
                .param("id", UUID.randomUUID())
                .param("layerId", layerId)
                .param("movementId", movementId)
                .param("quantity", quantity)
                .param("amount", amount)
                .update();
    }


    public boolean hasAllocations(UUID movementId) {
        return Boolean.TRUE.equals(
                jdbc.sql("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM inventory_cost_allocations
                        WHERE stock_movement_id = :movementId
                    )
                    """)
                        .param("movementId", movementId)
                        .query(Boolean.class)
                        .single()
        );
    }

    public List<InventoryCostLayer> lockAvailableLayers(
            UUID warehouseId,
            UUID variantId
    ) {
        return jdbc.sql("""
            SELECT
                id,
                warehouse_id,
                product_variant_id,
                source_movement_id,
                quantity_received,
                quantity_remaining,
                total_cost,
                remaining_cost,
                received_at
            FROM inventory_cost_layers
            WHERE warehouse_id = :warehouseId
              AND product_variant_id = :variantId
              AND quantity_remaining > 0
            ORDER BY received_at, id
            FOR UPDATE
            """)
                .param("warehouseId", warehouseId)
                .param("variantId", variantId)
                .query((rs, rowNum) -> new InventoryCostLayer(
                        rs.getObject("id", UUID.class),
                        rs.getObject("warehouse_id", UUID.class),
                        rs.getObject("product_variant_id", UUID.class),
                        rs.getObject("source_movement_id", UUID.class),
                        rs.getLong("quantity_received"),
                        rs.getLong("quantity_remaining"),
                        rs.getBigDecimal("total_cost"),
                        rs.getBigDecimal("remaining_cost"),
                        rs.getTimestamp("received_at").toInstant()
                ))
                .list();
    }

    public void createReceiptLayer(
            UUID movementId,
            UUID warehouseId,
            UUID variantId,
            long quantity,
            BigDecimal totalCost,
            Instant receivedAt
    ) {
        UUID layerId = UUID.randomUUID();

        jdbc.sql("""
                INSERT INTO inventory_cost_layers (
                    id, warehouse_id, product_variant_id,
                    source_movement_id, quantity_received,
                    quantity_remaining, total_cost,
                    remaining_cost, received_at
                )
                VALUES (
                    :id, :warehouse, :variant,
                    :movement, :quantity,
                    :quantity, :cost,
                    :cost, :receivedAt
                )
                """)
                .param("id", layerId)
                .param("warehouse", warehouseId)
                .param("variant", variantId)
                .param("movement", movementId)
                .param("quantity", quantity)
                .param("cost", totalCost)
                .param("receivedAt", java.time.OffsetDateTime.ofInstant(
                        receivedAt,
                        java.time.ZoneOffset.UTC
                ))
                .update();

        jdbc.sql("""
                INSERT INTO inventory_cost_movements (
                    id, cost_layer_id, stock_movement_id,
                    direction, quantity, amount
                )
                VALUES (
                    :id, :layer, :movement,
                    'IN', :quantity, :cost
                )
                """)
                .param("id", UUID.randomUUID())
                .param("layer", layerId)
                .param("movement", movementId)
                .param("quantity", quantity)
                .param("cost", totalCost)
                .update();
    }
}