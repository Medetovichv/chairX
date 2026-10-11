package kg.chairx.inventory.api;

import java.util.UUID;

/** Read-only sums across ALL stock lines of one warehouse, regardless of UI pagination.
 * Physical stock is separate from reservable stock. Never includes FIFO valuations.
 */
public record WarehouseStockSummary(
        UUID warehouseId, long onHand, long reserved, long blocked,
        long available, long stockedVariants, long lowStockVariants
) {}
