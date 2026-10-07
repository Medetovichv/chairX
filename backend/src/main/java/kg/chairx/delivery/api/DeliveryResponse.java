package kg.chairx.delivery.api;

import kg.chairx.delivery.domain.DeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID saleId,
        DeliveryStatus status,
        String recipientName,
        String recipientPhone,
        String address,
        String cityRegion,
        BigDecimal deliveryCost,
        String carrierName,
        String trackingNumber,
        String comment,
        String createdBy,
        Instant createdAt,
        String dispatchedBy,
        Instant dispatchedAt,
        String deliveredBy,
        Instant deliveredAt,
        String failedBy,
        Instant failedAt,
        String failureReason,
        String cancelledBy,
        Instant cancelledAt,
        String returnedToWarehouseBy,
        Instant returnedToWarehouseAt,
        UUID returnWarehouseId
) {
}