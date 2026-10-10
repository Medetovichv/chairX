package kg.chairx.inventory.api;

import java.util.List;
import java.util.UUID;

/** A paginated all-warehouse or selected-warehouse stock read model. */
public record InventoryOverviewPage(List<Line> items, int page, int size, long total) {
    public record WarehouseStock(UUID warehouseId, String warehouseCode,
                                 long onHand, long reserved, long blocked, long available) {
    }
    public record Line(UUID productVariantId, String model, String variation,
                       long onHand, long reserved, long blocked, long available,
                       List<WarehouseStock> warehouses) {
    }
}
