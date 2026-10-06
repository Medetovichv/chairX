package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchaseStatus;

public record PurchaseReceiptPageResponse(List<PurchaseReceiptSummary> items, int page, int size, long totalElements) { }
