package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchaseStatus;

public record PurchaseReceiptResponse(UUID id, UUID purchaseId, UUID warehouseId, UUID idempotencyKey,
        Instant postedAt, String createdBy, String comment, List<PurchaseReceiptItemResponse> items) { }
