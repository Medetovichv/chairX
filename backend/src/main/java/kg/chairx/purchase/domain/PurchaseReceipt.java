package kg.chairx.purchase.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseReceipt(UUID id, UUID purchaseId, UUID warehouseId, UUID idempotencyKey,
        String requestFingerprint, Instant postedAt, String createdBy, String comment,
        List<PurchaseReceiptItem> items) { }
