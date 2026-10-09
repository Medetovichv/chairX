package kg.chairx.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateInventoryTransferRequest(
        @NotNull UUID transferId,
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId,
        @NotNull UUID variantId,
        @Min(1) long quantity
) {}
