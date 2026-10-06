package kg.chairx.sale.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kg.chairx.sale.domain.FulfillmentType;

import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotNull UUID idempotencyKey,
        UUID customerId,
        @NotNull FulfillmentType fulfillmentType,
        @NotEmpty List<@Valid CreateSaleItemRequest> items
) {
}