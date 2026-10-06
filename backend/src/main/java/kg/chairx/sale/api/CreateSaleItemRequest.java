package kg.chairx.sale.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSaleItemRequest(
        @NotNull UUID productVariantId,
        @NotNull UUID warehouseId,
        @Positive long quantity,
        @NotNull
        @DecimalMin(value = "0")
        @Digits(integer = 19, fraction = 0)
        BigDecimal unitSalePrice
) {
}