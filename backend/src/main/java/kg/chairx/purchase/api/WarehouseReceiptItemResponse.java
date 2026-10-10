package kg.chairx.purchase.api;

import java.util.UUID;

/**
 * Public warehouse projection of one posted receipt line.
 * No unit costs, cargo allocation, or total cost are exposed here.
 */
public record WarehouseReceiptItemResponse(
        UUID id,
        UUID purchaseItemId,
        UUID productVariantId,
        long quantity
) {
    static WarehouseReceiptItemResponse from(PurchaseReceiptItemResponse item) {
        return new WarehouseReceiptItemResponse(item.id(), item.purchaseItemId(),
                item.productVariantId(), item.quantity());
    }
}
