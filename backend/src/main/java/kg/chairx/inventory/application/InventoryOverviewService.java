package kg.chairx.inventory.application;

import kg.chairx.common.web.InvalidQueryException;
import kg.chairx.inventory.api.InventoryOverviewPage;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryOverviewService {
    private final JdbcClient jdbc;
    private final WarehouseService warehouses;

    public InventoryOverviewService(JdbcClient jdbc, WarehouseService warehouses) {
        this.jdbc = jdbc;
        this.warehouses = warehouses;
    }

    public InventoryOverviewPage list(UUID warehouseId, String model, String variation,
                                       boolean includeZero, boolean onlyAvailable,
                                       int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (model != null && model.length() > 200)
                || (variation != null && variation.length() > 200)) {
            throw new InvalidQueryException("Некорректные параметры складской сводки");
        }
        if (warehouseId != null) warehouses.get(warehouseId);
        String modelQuery = model == null || model.isBlank() ? null : "%" + model.strip() + "%";
        String variationQuery = variation == null || variation.isBlank() ? null : "%" + variation.strip() + "%";

        String filter = """
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                WHERE (CAST(:model AS varchar) IS NULL OR p.name ILIKE :model)
                  AND (CAST(:variation AS varchar) IS NULL OR v.name ILIKE :variation)
                  AND (:includeZero = TRUE OR EXISTS (
                        SELECT 1 FROM inventory_balances b
                        WHERE b.product_variant_id = v.id
                          AND (CAST(:warehouse AS uuid) IS NULL OR b.warehouse_id = :warehouse)
                          AND (b.on_hand <> 0 OR b.reserved <> 0 OR b.blocked <> 0)))
                  AND (:onlyAvailable = FALSE OR EXISTS (
                        SELECT 1 FROM inventory_balances b
                        WHERE b.product_variant_id = v.id
                          AND (CAST(:warehouse AS uuid) IS NULL OR b.warehouse_id = :warehouse)
                          AND b.on_hand - b.reserved - b.blocked > 0))
                """;

        var sql = """
                WITH chosen AS (
                    SELECT v.id, p.name AS model, v.name AS variation
                """ + filter + """
                    ORDER BY p.name, v.name, v.id
                    LIMIT :size OFFSET :offset
                )
                SELECT chosen.id AS variant_id, chosen.model, chosen.variation,
                       w.id AS warehouse_id, w.code AS warehouse_code,
                       b.on_hand, b.reserved, b.blocked
                FROM chosen
                LEFT JOIN inventory_balances b ON b.product_variant_id = chosen.id
                    AND (CAST(:warehouse AS uuid) IS NULL OR b.warehouse_id = :warehouse)
                LEFT JOIN warehouses w ON w.id = b.warehouse_id
                ORDER BY chosen.model, chosen.variation, chosen.id, w.code
                """;

        var rows = jdbc.sql(sql)
                .param("model", modelQuery, Types.VARCHAR)
                .param("variation", variationQuery, Types.VARCHAR)
                .param("warehouse", warehouseId, Types.OTHER)
                .param("includeZero", includeZero)
                .param("onlyAvailable", onlyAvailable)
                .param("size", size).param("offset", (long)page*size)
                .query((rs, row) -> new Row(
                        rs.getObject("variant_id", UUID.class),
                        rs.getString("model"), rs.getString("variation"),
                        rs.getObject("warehouse_id",UUID.class),rs.getString("warehouse_code"),
                        rs.getLong("on_hand"),rs.getLong("reserved"),rs.getLong("blocked")))
                .list();

        Map<UUID, Accumulator> map = new LinkedHashMap<>();
        for (var row : rows) {
            var acc = map.computeIfAbsent(row.variantId(),
                    id -> new Accumulator(id,row.model(),row.variation()));
            if (row.warehouseId() != null) {
                acc.add(row);
            }
        }
        List<InventoryOverviewPage.Line> items = map.values().stream()
                .map(Accumulator::response).toList();

        long total = jdbc.sql("SELECT COUNT(*) " + filter)
                .param("model", modelQuery, Types.VARCHAR)
                .param("variation", variationQuery, Types.VARCHAR)
                .param("warehouse", warehouseId, Types.OTHER)
                .param("includeZero", includeZero)
                .param("onlyAvailable", onlyAvailable)
                .query(Long.class).single();
        return new InventoryOverviewPage(items, page, size, total);
    }

    private record Row(UUID variantId, String model, String variation,
                       UUID warehouseId, String warehouseCode,
                       long onHand, long reserved, long blocked) {
    }

    private static final class Accumulator {
        private final UUID id;
        private final String model;
        private final String variation;
        private long onHand, reserved, blocked;
        private final List<InventoryOverviewPage.WarehouseStock> stocks = new ArrayList<>();

        Accumulator(UUID id, String model, String variation) {
            this.id = id; this.model = model; this.variation = variation;
        }
        void add(Row row) {
            onHand += row.onHand();
            reserved += row.reserved();
            blocked += row.blocked();
            stocks.add(new InventoryOverviewPage.WarehouseStock(
                    row.warehouseId(), row.warehouseCode(), row.onHand(),
                    row.reserved(), row.blocked(),
                    row.onHand()-row.reserved()-row.blocked()));
        }
        InventoryOverviewPage.Line response() {
            return new InventoryOverviewPage.Line(id, model, variation,
                    onHand, reserved, blocked, onHand-reserved-blocked, List.copyOf(stocks));
        }
    }
}
