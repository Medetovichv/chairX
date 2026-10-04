package kg.chairx.product.api;

import java.util.List;

public record ProductPageResponse(List<ProductResponse> items, int page, int size,
        long totalElements, int totalPages) { }
