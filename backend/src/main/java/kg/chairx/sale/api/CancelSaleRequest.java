package kg.chairx.sale.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A reason is mandatory; comments remain optional and capped. */
public record CancelSaleRequest(
        @NotNull SaleCancellationReason reason,
        @Size(max = 1000) String comment
) {}
