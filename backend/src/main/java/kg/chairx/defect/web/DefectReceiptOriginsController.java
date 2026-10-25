package kg.chairx.defect.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.common.web.InvalidQueryException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Cost-free receipt provenance lookup for defect registration.
 * All filters are applied in SQL before pagination.
 */
@RestController
@RequestMapping("/api/defects/receipt-origins")
@Transactional(readOnly = true)
public class DefectReceiptOriginsController {
    private final JdbcClient jdbc;
    public DefectReceiptOriginsController(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Origin(UUID id, UUID purchaseId, UUID supplierId,
                         Instant postedAt, long quantity, String model, String variant,
                         String warehouse, String supplier) {}
    public record Page(List<Origin> items, int page, int size, long total) {}

    private static final String FROM = """
        FROM purchase_receipt_items ri
        JOIN purchase_receipts pr ON pr.id = ri.receipt_id AND pr.purchase_id = ri.purchase_id
        JOIN purchases p ON p.id = ri.purchase_id
        JOIN purchase_items pi ON pi.id = ri.purchase_item_id AND pi.purchase_id = ri.purchase_id
        JOIN product_variants pv ON pv.id = pi.product_variant_id
        JOIN products product ON product.id = pv.product_id
        JOIN warehouses w ON w.id = pr.warehouse_id
        JOIN suppliers s ON s.id = p.supplier_id
        WHERE pr.warehouse_id = :warehouseId AND pi.product_variant_id = :variantId
        """;

    public Page list(UUID warehouseId, UUID variantId, UUID supplierId, int page, int size) {
        return list(warehouseId, variantId, supplierId, page, size, null);
    }

    @GetMapping
    public Page list(@RequestParam UUID warehouseId, @RequestParam UUID variantId,
                     @RequestParam(required = false) UUID supplierId,
                     @RequestParam(defaultValue = "0") @Min(0) int page,
                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                     @RequestParam(required = false) LocalDate postedOn) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidQueryException("Некорректные параметры поиска поступлений");
        }
        String filter = FROM + (supplierId == null ? "" : " AND p.supplier_id = :supplierId")
                + (postedOn == null ? "" : " AND pr.posted_at >= :postedFrom AND pr.posted_at < :postedUntil");
        var list = jdbc.sql("""
                SELECT ri.id, p.id AS purchase_id, p.supplier_id, pr.posted_at,
                       ri.quantity, product.name AS model, pv.name AS variant,
                       w.name AS warehouse, s.name AS supplier
                """ + filter + " ORDER BY pr.posted_at DESC, ri.id DESC LIMIT :size OFFSET :offset");
        var count = jdbc.sql("SELECT COUNT(*) " + filter);
        list.param("warehouseId", warehouseId).param("variantId", variantId)
                .param("size", size).param("offset", (long) page * size);
        count.param("warehouseId", warehouseId).param("variantId", variantId);
        if (supplierId != null) {
            list.param("supplierId", supplierId);
            count.param("supplierId", supplierId);
        }
        if (postedOn != null) {
            ZoneId bishkek = ZoneId.of("Asia/Bishkek");
            Instant from = postedOn.atStartOfDay(bishkek).toInstant();
            Instant until = postedOn.plusDays(1).atStartOfDay(bishkek).toInstant();
            list.param("postedFrom", from).param("postedUntil", until);
            count.param("postedFrom", from).param("postedUntil", until);
        }
        List<Origin> items = list.query((rs, row) -> new Origin(
                rs.getObject("id", UUID.class), rs.getObject("purchase_id", UUID.class),
                rs.getObject("supplier_id", UUID.class), rs.getTimestamp("posted_at").toInstant(),
                rs.getLong("quantity"), rs.getString("model"), rs.getString("variant"),
                rs.getString("warehouse"), rs.getString("supplier"))).list();
        return new Page(items, page, size, count.query(Long.class).single());
    }
}
