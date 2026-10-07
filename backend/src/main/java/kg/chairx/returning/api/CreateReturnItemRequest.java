package kg.chairx.returning.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import kg.chairx.returning.domain.ReturnCondition;

public record CreateReturnItemRequest(
        @NotNull(message = "Укажите позицию продажи")
        UUID saleItemId,

        @Positive(message = "Количество возврата должно быть положительным")
        long quantity,

        @NotNull(message = "Укажите состояние возвращённого товара")
        ReturnCondition condition
) {
}