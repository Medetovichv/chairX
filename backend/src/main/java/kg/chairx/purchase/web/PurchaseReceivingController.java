package kg.chairx.purchase.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.common.web.InvalidQueryException;
import kg.chairx.purchase.domain.PurchaseStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Restricted warehouse receiving worklist. Never returns unit costs, cargo amounts,
 * supplier payments or financial account information.
 */
@RestController
@RequestMapping("/api/purchases")
@Transactional(readOnly = true)
public class PurchaseReceivingController {
    private final JdbcClient jdbc;

    public PurchaseReceivingController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Line(UUID purchaseId, PurchaseStatus status, Instant createdAt,
                       UUID purchaseItemId, UUID productVariantId, String model,
                       String variant, long orderedQuantity, long receivedQuantity,
                       long remainingQuantity) { }

    public record Worklist(List<Line> items, int page, int size, long total) { }
    public record Summary(UUID purchaseId, PurchaseStatus status, List<Line> items) { }

    private static final String PROJECTION = """
            SELECT p.id AS purchase_id, p.status, p.created_at,
                   pi.id AS purchase_item_id, pi.product_variant_id,
                   product.name AS model, variant.name AS variant,
                   pi.ordered_quantity, pi.received_quantity
            FROM purchases p
            JOIN purchase_items pi ON pi.purchase_id = p.id
            JOIN product_variants variant ON variant.id = pi.product_variant_id
            JOIN products product ON product.id = variant.product_id
            """;

    @GetMapping("/receiving")
    public Worklist worklist(@RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        validatePage(page, size);
        String where = """
                WHERE p.status IN ('CONFIRMED', 'PARTIALLY_RECEIVED')
                  AND pi.received_quantity < pi.ordered_quantity
                """;
        List<Line> lines = jdbc.sql(PROJECTION + where +
                        " ORDER BY p.created_at DESC,p.id DESC,pi.line_number LIMIT :size OFFSET :offset")
                .param("size", size)
                .param("offset", (long) page * size)
                .query(PurchaseReceivingController::mapLine).list();
        long total = jdbc.sql("""
                SELECT COUNT(*) FROM purchases p
                JOIN purchase_items pi ON pi.purchase_id = p.id
                """ + where).query(Long.class).single();
        return new Worklist(lines, page, size, total);
    }

    @GetMapping("/{id}/receiving-summary")
    public Summary summary(@PathVariable UUID id) {
        List<Line> lines = jdbc.sql(PROJECTION + """
                WHERE p.id = :id AND p.status IN ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED')
                ORDER BY pi.line_number
                """)
                .param("id", id)
                .query(PurchaseReceivingController::mapLine).list();
        if (lines.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Закупка для приёмки не найдена");
        }
        return new Summary(id, lines.getFirst().status(), lines);
    }

    private static Line mapLine(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long ordered = rs.getLong("ordered_quantity");
        long received = rs.getLong("received_quantity");
        return new Line(rs.getObject("purchase_id", UUID.class),
                PurchaseStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("purchase_item_id", UUID.class),
                rs.getObject("product_variant_id", UUID.class),
                rs.getString("model"), rs.getString("variant"),
                ordered, received, ordered - received);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidQueryException("Некорректные параметры списка приёмки");
        }
    }
}
