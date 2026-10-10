package kg.chairx.inventory.api;

import kg.chairx.inventory.domain.InventoryBalance;
import java.util.UUID;

/** Explicit available field for frontend clients; GET has no physical effects. */
public record InventoryBalanceResponse(
        UUID warehouseId,UUID productVariantId,
        long onHand,long reserved,long blocked,long available
) {
    public static InventoryBalanceResponse from(InventoryBalance value) {
        return new InventoryBalanceResponse(
                value.warehouseId(),value.productVariantId(),
                value.onHand(),value.reserved(),value.blocked(),value.available());
    }
}
