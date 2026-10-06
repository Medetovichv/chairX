package kg.chairx.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Trusted internal command for changing blocked stock.
 *
 * Blocking does not represent a physical stock movement and therefore
 * must not create a StockMovement.
 */
public record ChangeBlockedStock(
        @NotNull(message = "Укажите склад")
        UUID warehouseId,

        @NotNull(message = "Укажите вариант товара")
        UUID productVariantId,

        @Positive(message = "Количество должно быть положительным")
        long quantity,

        @NotNull(message = "Укажите источник операции")
        UUID sourceId,

        @Size(min = 1, max = 200,
                message = "Имя инициатора должно содержать от 1 до 200 символов")
        String actor
) {
}