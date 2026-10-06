package kg.chairx.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseReceiptRequest(
        @NotNull(message = "Укажите ключ поступления") UUID idempotencyKey,
        @NotNull(message = "Укажите склад") UUID warehouseId,
        @NotEmpty(message = "Добавьте позиции поступления") @Size(max = 1000, message = "Допустимо не более 1000 позиций")
        List<@NotNull(message = "Позиция не может быть пустой") @Valid ReceiptItemRequest> items,
        @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment) { }
