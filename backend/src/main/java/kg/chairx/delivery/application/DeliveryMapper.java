package kg.chairx.delivery.application;

import kg.chairx.delivery.api.DeliveryResponse;
import kg.chairx.delivery.domain.Delivery;

public final class DeliveryMapper {

    private DeliveryMapper() {
    }

    public static DeliveryResponse toResponse(
            Delivery delivery
    ) {
        return new DeliveryResponse(
                delivery.id(),
                delivery.saleId(),
                delivery.status(),
                delivery.recipientName(),
                delivery.recipientPhone(),
                delivery.address(),
                delivery.cityRegion(),
                delivery.deliveryCost(),
                delivery.carrierName(),
                delivery.trackingNumber(),
                delivery.comment(),
                delivery.createdBy(),
                delivery.createdAt(),
                delivery.dispatchedBy(),
                delivery.dispatchedAt(),
                delivery.deliveredBy(),
                delivery.deliveredAt(),
                delivery.failedBy(),
                delivery.failedAt(),
                delivery.failureReason(),
                delivery.cancelledBy(),
                delivery.cancelledAt(),
                delivery.returnedToWarehouseBy(),
                delivery.returnedToWarehouseAt(),
                delivery.returnWarehouseId()
        );
    }
}