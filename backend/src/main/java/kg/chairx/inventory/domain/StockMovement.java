package kg.chairx.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record StockMovement(UUID id, UUID operationId, UUID warehouseId, UUID productVariantId,
        StockMovementType type, long quantity, String sourceType, UUID sourceId,
        String actor, Instant occurredAt) { }
