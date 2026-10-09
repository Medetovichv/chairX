package kg.chairx.inventory.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryTransferDetailsResponse(
        UUID id,
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        UUID variantId,
        long quantity,
        BigDecimal totalCost,
        String actor,
        OffsetDateTime createdAt,
        UUID outMovementId,
        UUID inMovementId
) {}
