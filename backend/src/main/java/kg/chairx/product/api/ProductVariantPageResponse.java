package kg.chairx.product.api;

import java.util.List;

public record ProductVariantPageResponse(List<ProductVariantResponse> items, int page, int size,
        long totalElements, int totalPages) { }
