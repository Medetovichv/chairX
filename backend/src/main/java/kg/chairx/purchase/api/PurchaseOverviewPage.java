package kg.chairx.purchase.api;

import kg.chairx.purchase.domain.PurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Aggregated purchasing projection; receipts and cargo are real posted values. */
public record PurchaseOverviewPage(List<Line> items, int page, int size, long total) {
    public record Line(UUID id, UUID supplierId, String supplierName,
                       PurchaseStatus status, Instant createdAt,
                       long itemCount, long orderedQuantity, long receivedQuantity,
                       long remainingQuantity, BigDecimal goodsCost, BigDecimal cargoCost,
                       BigDecimal totalCost, Instant lastReceiptAt) {
    }
}
