package kg.chairx.returning.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Return(
        UUID id,
        UUID saleId,
        UUID warehouseId,
        List<ReturnItem> items,
        String reason,
        String comment,
        String createdBy,
        Instant createdAt
) {
    public Return {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");

        items = List.copyOf(items);
    }
}