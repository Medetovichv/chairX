package kg.chairx.defect.domain;

import java.util.UUID;

public record ReceiptItemOrigin(
        UUID receiptItemId,
        UUID warehouseId,
        UUID productVariantId,
        UUID supplierId
) {
}