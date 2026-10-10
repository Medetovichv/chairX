package kg.chairx.inventory.application;

import kg.chairx.inventory.api.InventoryTransferDetailsResponse;
import kg.chairx.inventory.api.InventoryTransferPageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryTransferQueryService {

    private final JdbcClient jdbc;

    public InventoryTransferQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public InventoryTransferDetailsResponse get(UUID id) {
        return jdbc.sql("""
                SELECT
                    t.id,
                    t.source_warehouse_id,
                    t.destination_warehouse_id,
                    t.product_variant_id,
                    t.quantity,
                    t.actor,
                    t.created_at,
                    t.out_movement_id,
                    t.in_movement_id
                FROM inventory_transfers t
                WHERE t.id = :id
                  AND t.out_movement_id IS NOT NULL
                  AND t.in_movement_id IS NOT NULL
                """)
                .param("id", id)
                .query(this::map)
                .optional()
                .orElseThrow(() ->
                        new InventoryTransferNotFoundException(id)
                );
    }

    public InventoryTransferPageResponse list(
            UUID warehouseId,
            UUID variantId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidTransferQueryException(
                    "Invalid pagination parameters"
            );
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransferQueryException(
                    "from must not be after to"
            );
        }

        StringBuilder filter = new StringBuilder("""
            FROM inventory_transfers t
            WHERE t.out_movement_id IS NOT NULL
              AND t.in_movement_id IS NOT NULL
            """);

        if (warehouseId != null) {
            filter.append("""
                AND (
                    t.source_warehouse_id = :warehouseId
                    OR t.destination_warehouse_id = :warehouseId
                )
                """);
        }

        if (variantId != null) {
            filter.append("""
                AND t.product_variant_id = :variantId
                """);
        }

        if (from != null) {
            filter.append("""
                AND t.created_at >= :fromDate
                """);
        }

        if (to != null) {
            filter.append("""
                AND t.created_at <= :toDate
                """);
        }

        Map<String, Object> params = new HashMap<>();

        if (warehouseId != null) {
            params.put("warehouseId", warehouseId);
        }

        if (variantId != null) {
            params.put("variantId", variantId);
        }

        if (from != null) {
            params.put("fromDate", from);
        }

        if (to != null) {
            params.put("toDate", to);
        }

        long total = jdbc.sql("SELECT COUNT(*) " + filter)
                .params(params)
                .query(Long.class)
                .single();

        String sql = """
            SELECT
                t.id,
                t.source_warehouse_id,
                t.destination_warehouse_id,
                t.product_variant_id,
                t.quantity,
                t.actor,
                t.created_at,
                t.out_movement_id,
                t.in_movement_id
            """ + filter + """
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT :limit OFFSET :offset
            """;

        params.put("limit", size);
        params.put("offset", (long) page * size);

        List<InventoryTransferDetailsResponse> items = jdbc.sql(sql)
                .params(params)
                .query(this::map)
                .list();

        int totalPages = (int) (
                total / size + (total % size == 0 ? 0 : 1)
        );

        return new InventoryTransferPageResponse(
                items,
                page,
                size,
                total,
                totalPages
        );
    }

    private InventoryTransferDetailsResponse map(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new InventoryTransferDetailsResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("source_warehouse_id", UUID.class),
                rs.getObject("destination_warehouse_id", UUID.class),
                rs.getObject("product_variant_id", UUID.class),
                rs.getLong("quantity"),
                rs.getString("actor"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("out_movement_id", UUID.class),
                rs.getObject("in_movement_id", UUID.class)
        );
    }
}
