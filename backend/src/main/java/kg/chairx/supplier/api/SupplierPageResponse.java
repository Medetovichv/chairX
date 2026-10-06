package kg.chairx.supplier.api;

import java.util.List;

public record SupplierPageResponse(List<SupplierResponse> items, int page, int size,
        long totalElements, int totalPages) { }
