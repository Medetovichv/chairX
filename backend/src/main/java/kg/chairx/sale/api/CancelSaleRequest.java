package kg.chairx.sale.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.sale.domain.SaleCancellationReason;

/** Input contract for a new cancellation; the controller/service must enforce it before writing. */
public record CancelSaleRequest(
        @NotNull SaleCancellationReason reason,
        @Size(max = 1000) String comment
) {}
