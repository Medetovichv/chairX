package kg.chairx.sale.domain;

/** Stable, selectable cancellation codes; labels are localized in the client. */
public enum SaleCancellationReason {
    CUSTOMER_CHANGED_MIND,
    PRODUCT_NOT_SUITABLE,
    PRICE_NOT_ACCEPTABLE,
    DUPLICATE_ORDER,
    DELIVERY_UNAVAILABLE,
    OTHER
}
