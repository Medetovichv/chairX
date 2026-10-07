package kg.chairx.returning.domain;

import java.util.Objects;
import java.util.UUID;

public record ReturnItem(
        UUID id,
        UUID returnId,
        UUID saleItemId,
        long quantity,
        ReturnCondition condition
) {
    public ReturnItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(returnId, "returnId");
        Objects.requireNonNull(saleItemId, "saleItemId");
        Objects.requireNonNull(condition, "condition");

        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество возврата должно быть положительным");
        }
    }
}