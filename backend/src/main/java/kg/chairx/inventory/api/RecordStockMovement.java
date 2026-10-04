package kg.chairx.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kg.chairx.inventory.domain.StockMovementType;

/** Trusted internal command. One stable operationId per source line and physical effect. */
public record RecordStockMovement(
        @NotNull(message = "Укажите ключ операции") UUID operationId,
        @NotNull(message = "Укажите склад") UUID warehouseId,
        @NotNull(message = "Укажите вариант товара") UUID productVariantId,
        @NotNull(message = "Укажите тип движения") StockMovementType type,
        @Positive(message = "Количество должно быть положительным") long quantity,
        @NotNull(message = "Укажите тип источника")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}", message = "Некорректный тип источника") String sourceType,
        @NotNull(message = "Укажите источник операции") UUID sourceId,
        @Size(min = 1, max = 200, message = "Имя инициатора должно содержать от 1 до 200 символов") String actor) { }
