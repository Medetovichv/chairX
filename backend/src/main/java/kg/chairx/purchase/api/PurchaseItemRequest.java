package kg.chairx.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseItemRequest(
        @NotNull(message = "Укажите вариант товара") UUID productVariantId,
        @Positive(message = "Заказанное количество должно быть положительным") long orderedQuantity,
        @NotNull(message = "Укажите закупочную цену")
        @DecimalMin(value = "0", message = "Цена не может быть отрицательной")
        @Digits(integer = 17, fraction = 2, message = "Цена: не более 17 целых и 2 дробных знаков") BigDecimal purchaseUnitCost) { }
