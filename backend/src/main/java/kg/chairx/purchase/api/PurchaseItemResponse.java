package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchaseStatus;

public record PurchaseItemResponse(UUID id, UUID productVariantId, int lineNumber, long orderedQuantity,
        long receivedQuantity, BigDecimal purchaseUnitCost, BigDecimal allocatedCargoCost, BigDecimal finalUnitCost,
        UUID productId, String modelName, String variantName, String sku) {
    public PurchaseItemResponse(UUID id, UUID productVariantId, int lineNumber,
            long orderedQuantity, long receivedQuantity, BigDecimal purchaseUnitCost,
            BigDecimal allocatedCargoCost, BigDecimal finalUnitCost) {
        this(id, productVariantId, lineNumber, orderedQuantity, receivedQuantity, purchaseUnitCost,
                allocatedCargoCost, finalUnitCost, null, null, null, null);
    }
}
