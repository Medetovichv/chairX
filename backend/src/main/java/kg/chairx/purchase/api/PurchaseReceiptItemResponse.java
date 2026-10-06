package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchaseStatus;

public record PurchaseReceiptItemResponse(UUID id, UUID purchaseItemId, UUID productVariantId,
        long quantity, BigDecimal allocatedCargoCost, BigDecimal totalCost) { }
