package kg.chairx.customer.api;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerResponse> items,
        int page,
        int size,
        long totalElements
) {
}