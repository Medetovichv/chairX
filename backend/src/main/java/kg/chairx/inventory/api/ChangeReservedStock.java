package kg.chairx.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ChangeReservedStock(
        @NotNull UUID warehouseId,
        @NotNull UUID productVariantId,
        @Positive long quantity
) {
}