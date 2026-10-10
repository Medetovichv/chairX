package kg.chairx.sale.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.sale.domain.FulfillmentType;
import java.util.List;

/** Full replacement of the editable fields of a DRAFT sale. */
public record UpdateDraftSaleRequest(
        java.util.UUID customerId,
        FulfillmentType fulfillmentType,
        List<@NotNull @Valid CreateSaleItemRequest> items,
        @Size(max = 2000) String comment
) {
}
