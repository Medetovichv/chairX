package kg.chairx.sale.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.delivery.domain.DeliveryStatus;

/** Paginated operational sale projection, independent of financial day closing. */
public record SaleSummary(
        UUID id, String saleNumber, Instant createdAt,
        UUID customerId, String customerName, BigDecimal total,
        SaleStatus status, FulfillmentType fulfillmentType,
        long quantity, String products, String paymentStatus,
        DeliveryStatus deliveryStatus, LocalDate plannedDeliveryDate
) {
    public SaleSummary(UUID id, String saleNumber, Instant createdAt,
                       UUID customerId, String customerName, BigDecimal total,
                       SaleStatus status, FulfillmentType fulfillmentType) {
        this(id, saleNumber, createdAt, customerId, customerName, total,
                status, fulfillmentType, 0, null, null, null, null);
    }
}
