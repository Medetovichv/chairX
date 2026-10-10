package kg.chairx.purchase.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for POST /api/purchases/{id}/receipts (INVENTORY_RECEIVE).
 *
 * Keep PurchaseReceiptResponse, including its cost fields, exclusively inside
 * the service/domain and on separately authorized PURCHASE_READ endpoints.
 * Mapping occurs after the transactional receiving operation has completed.
 */
public record WarehouseReceiptResponse(
        UUID id,
        UUID purchaseId,
        UUID warehouseId,
        UUID idempotencyKey,
        Instant postedAt,
        String createdBy,
        String comment,
        List<WarehouseReceiptItemResponse> items
) {
    public static WarehouseReceiptResponse from(PurchaseReceiptResponse receipt) {
        return new WarehouseReceiptResponse(
                receipt.id(),
                receipt.purchaseId(),
                receipt.warehouseId(),
                receipt.idempotencyKey(),
                receipt.postedAt(),
                receipt.createdBy(),
                receipt.comment(),
                receipt.items().stream()
                        .map(WarehouseReceiptItemResponse::from)
                        .toList()
        );
    }
}
