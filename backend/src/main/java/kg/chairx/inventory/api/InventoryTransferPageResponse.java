package kg.chairx.inventory.api;

import java.util.List;

public record InventoryTransferPageResponse(
        List<InventoryTransferDetailsResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
