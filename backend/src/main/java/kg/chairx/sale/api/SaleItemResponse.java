package kg.chairx.sale.api;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        UUID productVariantId,
        UUID warehouseId,
        long quantity,
        BigDecimal unitSalePrice,
        BigDecimal total
) {
}