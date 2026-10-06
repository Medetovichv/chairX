package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReceiptItem(UUID id, UUID purchaseItemId, UUID productVariantId, long quantity,
        BigDecimal allocatedCargoCost, BigDecimal totalCost) { }
