package kg.chairx.product.api;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String name,
        String sku,
        String color,
        BigDecimal recommendedSalePrice,
        boolean active,
        Instant createdAt,
        Instant updatedAt) { }
