package kg.chairx.sale.api;

import java.time.Instant;

/** Public operational history. Raw audit before/after JSON is never serialized. */
public record SaleHistoryEvent(String action, String actor, Instant occurredAt) {
}
