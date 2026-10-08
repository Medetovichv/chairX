package kg.chairx.exchange.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.domain.FulfillmentType;

import java.util.List;
import java.util.UUID;

public record CreateExchangeRequest(
        @NotNull UUID idempotencyKey,
        @NotNull UUID returnId,
        @NotNull FulfillmentType fulfillmentType,
        @NotEmpty List<@Valid CreateSaleItemRequest> items
) {
}