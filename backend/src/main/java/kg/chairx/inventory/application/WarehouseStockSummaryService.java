package kg.chairx.inventory.application;

import kg.chairx.inventory.api.WarehouseStockSummary;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WarehouseStockSummaryService {
    private final JdbcClient jdbc;

    public WarehouseStockSummaryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Includes empty/inactive warehouses for historical stock visibility.
     * Counts only warehouse inventory; not product prices or business money.
     */
    public List<WarehouseStockSummary> overview() {
        return jdbc.sql("""
                SELECT w.id AS warehouse_id,
                       COALESCE(SUM(b.on_hand),0) AS on_hand,
                       COALESCE(SUM(b.reserved),0) AS reserved,
                       COALESCE(SUM(b.blocked),0) AS blocked,
                       COALESCE(SUM(b.on_hand-b.reserved-b.blocked),0) AS available,
                       COUNT(b.product_variant_id) FILTER (WHERE b.on_hand > 0) AS stocked_variants,
                       COUNT(b.product_variant_id) FILTER (
                           WHERE b.on_hand-b.reserved-b.blocked BETWEEN 1 AND 5
                       ) AS low_stock_variants
                FROM warehouses w
                LEFT JOIN inventory_balances b ON b.warehouse_id = w.id
                GROUP BY w.id
                ORDER BY w.id
                """)
                .query((rs, row) -> new WarehouseStockSummary(
                        rs.getObject("warehouse_id", java.util.UUID.class),
                        rs.getLong("on_hand"), rs.getLong("reserved"),
                        rs.getLong("blocked"), rs.getLong("available"),
                        rs.getLong("stocked_variants"), rs.getLong("low_stock_variants")
                )).list();
    }
}
