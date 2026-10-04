package kg.chairx.warehouse.api;

import java.util.List;

public record WarehousePageResponse(List<WarehouseResponse> items, int page, int size,
        long totalElements, int totalPages) { }
