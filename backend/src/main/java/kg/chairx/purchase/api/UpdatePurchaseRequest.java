package kg.chairx.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdatePurchaseRequest(
        @NotNull(message = "Укажите поставщика") UUID supplierId,
        @NotEmpty(message = "Добавьте позиции закупки") @Size(max = 1000, message = "Допустимо не более 1000 позиций")
        List<@NotNull(message = "Позиция не может быть пустой") @Valid PurchaseItemRequest> items,
        @DecimalMin(value = "0", message = "Карго не может быть отрицательным")
        @Digits(integer = 17, fraction = 2, message = "Карго: не более 17 целых и 2 дробных знаков") BigDecimal cargoCost,
        @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment) { }
