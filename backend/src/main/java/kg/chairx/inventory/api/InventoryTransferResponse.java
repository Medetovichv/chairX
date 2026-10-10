package kg.chairx.inventory.api;

import java.util.UUID;

public record InventoryTransferResponse(
        UUID transferId,
        UUID outMovementId,
        UUID inMovementId,
        long quantity
) {}
