package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Purchase(UUID id, UUID supplierId, PurchaseStatus status, BigDecimal cargoCost,
        String cargoAllocationMethod, Instant costsLockedAt, Instant confirmedAt, String comment,
        String createdBy, Instant createdAt, Instant updatedAt) { }
