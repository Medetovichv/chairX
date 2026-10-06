package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItem(UUID id, UUID purchaseId, UUID productVariantId, int lineNumber,
        long orderedQuantity, long receivedQuantity, BigDecimal purchaseUnitCost,
        BigDecimal allocatedCargoCost, BigDecimal finalUnitCost) { }
