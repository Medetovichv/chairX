package kg.chairx.sale.api;

/** Fixed, server-authoritative reason catalog, never free-text status codes. */
public enum SaleCancellationReason {
    CUSTOMER_CHANGED_MIND,
    INSUFFICIENT_FUNDS,
    PRODUCT_MISMATCH,
    DUPLICATE_ORDER,
    OTHER
}
