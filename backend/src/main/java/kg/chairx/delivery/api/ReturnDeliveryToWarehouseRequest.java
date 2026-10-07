package kg.chairx.delivery.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnDeliveryToWarehouseRequest(
        @NotNull
        UUID warehouseId
) {
}