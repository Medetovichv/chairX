package kg.chairx.inventory.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryCostLayer(
        UUID id,
        UUID warehouseId,
        UUID productVariantId,
        UUID sourceMovementId,
        long quantityReceived,
        long quantityRemaining,
        BigDecimal totalCost,
        BigDecimal remainingCost,
        Instant receivedAt
) {
}