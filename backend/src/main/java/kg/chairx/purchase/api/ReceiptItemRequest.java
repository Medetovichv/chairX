package kg.chairx.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReceiptItemRequest(
        @NotNull(message = "Укажите позицию закупки") UUID purchaseItemId,
        @Positive(message = "Количество поступления должно быть положительным") long quantity) { }
