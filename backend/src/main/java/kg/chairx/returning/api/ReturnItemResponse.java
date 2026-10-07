package kg.chairx.returning.api;

import java.util.UUID;
import kg.chairx.returning.domain.ReturnCondition;

public record ReturnItemResponse(
        UUID id,
        UUID saleItemId,
        long quantity,
        ReturnCondition condition
) {
}