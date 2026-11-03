package kg.chairx.sale.api;

import java.time.Instant;

/** Returned only through the existing authorized sale detail API. */
public record SaleCancellationDetails(
        SaleCancellationReason reason,
        String comment,
        String cancelledBy,
        Instant cancelledAt
) {}
