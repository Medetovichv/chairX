package kg.chairx.returning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID saleId,
        UUID warehouseId,
        List<ReturnItemResponse> items,
        String reason,
        String comment,
        String createdBy,
        Instant createdAt
) {
}