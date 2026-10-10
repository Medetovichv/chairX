package kg.chairx.sale.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.sale.domain.FulfillmentType;
import java.util.List;
import java.util.UUID;

/** DRAFT may have no items, customer, or fulfillment method. */
public record CreateDraftSaleRequest(
        @NotNull UUID idempotencyKey,
        UUID customerId,
        FulfillmentType fulfillmentType,
        List<@NotNull @Valid CreateSaleItemRequest> items,
        @Size(max = 2000) String comment
) {
}
