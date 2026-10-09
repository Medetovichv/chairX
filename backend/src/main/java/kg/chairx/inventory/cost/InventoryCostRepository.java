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

    public BigDecimal findOriginalCost(UUID movementId) {
        return jdbc.sql("""
            SELECT total_cost
            FROM inventory_cost_layers
            WHERE source_movement_id = :movementId
            """)
                .param("movementId", movementId)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    public boolean hasCostLayer(UUID movementId) {
        return Boolean.TRUE.equals(
                jdbc.sql("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM inventory_cost_layers
                        WHERE source_movement_id = :movementId
                    )
                    """)
                        .param("movementId", movementId)
                        .query(Boolean.class)
                        .single()
        );
    }

    public void lockMovement(UUID movementId) {
        var found = jdbc.sql("""
            SELECT id
            FROM stock_movements
            WHERE id = :movementId
            FOR UPDATE
            """)
                .param("movementId", movementId)
                .query(UUID.class)
                .optional();

        if (found.isEmpty()) {
            throw new IllegalArgumentException(
                    "Stock movement not found: " + movementId
            );
        }
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

    public BigDecimal consumedCost(UUID movementId) {
        return jdbc.sql("SELECT sum(allocated_cost) FROM inventory_cost_allocations WHERE stock_movement_id=:id")
                .param("id", movementId).query(BigDecimal.class).optional().orElse(null);
    }

    public UUID saleMovement(UUID saleItemId) {
        return jdbc.sql("""
                SELECT id FROM stock_movements
                WHERE movement_type='SALE_OUT' AND source_type='SALE_ITEM' AND source_id=:id
                FOR UPDATE
                """).param("id", saleItemId).query(UUID.class).optional()
                .orElseThrow(() -> new InventoryCostException("ORIGINAL_COST_MISSING", "Не найдено исходное списание продажи"));
    }

    public void requireReturnOrigin(UUID originalMovement, UUID variant) {
        boolean valid = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM stock_movements
                WHERE id=:id AND movement_type='SALE_OUT' AND product_variant_id=:variant)
                """).param("id", originalMovement).param("variant", variant).query(Boolean.class).single();
        if (!valid) throw new InventoryCostException("RETURN_COST_ORIGIN_MISMATCH", "Возврат не соответствует исходной продаже");
    }

    public boolean sameRestorationOrigin(UUID returnMovement, UUID originalMovement) {
        return jdbc.sql("""
                SELECT count(*) > 0 AND bool_and(a.stock_movement_id=:original)
                FROM inventory_cost_restorations r
                JOIN inventory_cost_allocations a ON a.id=r.original_allocation_id
                WHERE r.return_movement_id=:returned
                """).param("original", originalMovement).param("returned", returnMovement).query(Boolean.class).single();
    }

    public List<CostRestorationAllocation> restorationAllocations(UUID originalMovement) {
        return jdbc.sql("""
                SELECT a.id,a.quantity,a.allocated_cost,
                       COALESCE(sum(r.quantity),0) AS returned_quantity,
                       COALESCE(sum(r.amount),0) AS returned_amount
                FROM inventory_cost_allocations a
                JOIN inventory_cost_layers l ON l.id=a.cost_layer_id
                LEFT JOIN inventory_cost_restorations r ON r.original_allocation_id=a.id
                WHERE a.stock_movement_id=:id
                GROUP BY a.id,l.received_at,l.id
                ORDER BY l.received_at,l.id,a.id
                """).param("id", originalMovement).query((rs,n) -> new CostRestorationAllocation(
                        rs.getObject("id", UUID.class),rs.getLong("quantity"),rs.getBigDecimal("allocated_cost"),
                        rs.getLong("returned_quantity"),rs.getBigDecimal("returned_amount"))).list();
    }

    public void recordRestoration(UUID movement, UUID allocation, long quantity, BigDecimal amount) {
        jdbc.sql("""
                INSERT INTO inventory_cost_restorations(return_movement_id,original_allocation_id,quantity,amount)
                VALUES (:movement,:allocation,:quantity,:amount)
                """).param("movement",movement).param("allocation",allocation)
                .param("quantity",quantity).param("amount",amount).update();
    }

    public void requireNoUnvaluedReturns(UUID saleItemId) {
        // A historical physical return without valuation cannot safely be re-costed using a new request.
        boolean missing = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM stock_movements m
                    WHERE m.movement_type='RETURN_IN' AND NOT EXISTS (
                        SELECT 1 FROM inventory_cost_restorations cr WHERE cr.return_movement_id=m.id)
                    AND (
                        (m.source_type='SALE_RETURN' AND EXISTS (
                            SELECT 1 FROM return_items ri WHERE ri.id=m.operation_id AND ri.sale_item_id=:item))
                        OR (m.source_type='DELIVERY_RETURN' AND EXISTS (
                            SELECT 1 FROM deliveries d JOIN sale_items si ON si.sale_id=d.sale_id
                            WHERE d.id=m.source_id AND si.id=:item))
                    ))
                """).param("item",saleItemId).query(Boolean.class).single();
        if (missing) throw new InventoryCostException("LEGACY_RETURN_COST_MISSING",
                "Есть исторический возврат без себестоимости; требуется сверка данных");
    }

    public List<InventoryCostLayer> lockReceiptLayer(UUID warehouse, UUID variant, UUID receiptItem) {
        return lockAvailableLayers(warehouse, variant).stream()
                .filter(layer -> jdbc.sql("SELECT operation_id FROM stock_movements WHERE id=:id")
                        .param("id", layer.sourceMovementId()).query(UUID.class).single().equals(receiptItem))
                .toList();
    }

    public void recordWriteOffOrigin(UUID movement, UUID receiptItem) {
        jdbc.sql("INSERT INTO inventory_cost_write_offs(stock_movement_id,purchase_receipt_item_id) VALUES (:id,:receipt)")
                .param("id",movement).param("receipt",receiptItem,java.sql.Types.OTHER).update();
    }

    public boolean sameWriteOffOrigin(UUID movement, UUID receiptItem) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM inventory_cost_write_offs WHERE stock_movement_id=:id
                    AND purchase_receipt_item_id IS NOT DISTINCT FROM CAST(:receipt AS UUID))
                """).param("id",movement).param("receipt",receiptItem,java.sql.Types.OTHER).query(Boolean.class).single();
    }
}
