package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchaseStatus;

public record PurchaseResponse(UUID id, UUID supplierId, PurchaseStatus status, BigDecimal cargoCost,
        String cargoAllocationMethod, Instant costsLockedAt, Instant confirmedAt, String comment,
        String createdBy, Instant createdAt, Instant updatedAt, List<PurchaseItemResponse> items) { }
