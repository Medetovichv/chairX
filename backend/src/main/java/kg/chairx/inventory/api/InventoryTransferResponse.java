package kg.chairx.inventory.api;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryTransferResponse(
        UUID transferId,
        UUID outMovementId,
        UUID inMovementId,
        long quantity,
        BigDecimal totalCost
) {}
