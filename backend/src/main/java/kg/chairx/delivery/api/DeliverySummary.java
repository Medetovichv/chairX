package kg.chairx.delivery.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.sale.domain.FulfillmentType;

public record DeliverySummary(
        UUID id, UUID saleId, DeliveryStatus status, String recipientName,
        String cityRegion, Instant createdAt, LocalDate plannedDeliveryDate,
        String saleNumber, String recipientPhone, String address,
        FulfillmentType fulfillmentType, Instant deliveredAt
) {
    public DeliverySummary(UUID id, UUID saleId, DeliveryStatus status, String recipientName,
                           String cityRegion, Instant createdAt, LocalDate plannedDeliveryDate) {
        this(id, saleId, status, recipientName, cityRegion, createdAt, plannedDeliveryDate,
                null, null, null, null, null);
    }
    public DeliverySummary(UUID id, UUID saleId, DeliveryStatus status, String recipientName,
                           String cityRegion, Instant createdAt) {
        this(id, saleId, status, recipientName, cityRegion, createdAt, null);
    }
}
