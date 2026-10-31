package kg.chairx.sale.domain;

/** Stable reason codes for new sale cancellations; display labels belong to clients. */
public enum SaleCancellationReason {
    CUSTOMER_CHANGED_MIND,
    PRODUCT_NOT_AS_EXPECTED,
    INSUFFICIENT_FUNDS,
    DUPLICATE_ORDER,
    PRODUCT_UNAVAILABLE,
    DELIVERY_UNAVAILABLE,
    OTHER
}
