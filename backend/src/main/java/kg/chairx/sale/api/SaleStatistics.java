package kg.chairx.sale.api;

import java.math.BigDecimal;

/** Accurate, aggregated last-30-calendar-day sales metrics for the sales UI.
 * Contains sale prices only; never inventory FIFO costs or margins.
 */
public record SaleStatistics(long orders30Days, long fulfilled30Days,
                             long inWork30Days, BigDecimal averageCheck30Days) {}
