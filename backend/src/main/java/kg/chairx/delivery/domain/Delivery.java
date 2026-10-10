package kg.chairx.delivery.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Delivery(
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
        UUID returnWarehouseId,
        LocalDate plannedDeliveryDate
) {

    public Delivery(UUID id, UUID saleId, DeliveryStatus status, String recipientName, String recipientPhone, String address, String cityRegion, BigDecimal deliveryCost, String carrierName, String trackingNumber, String comment, String createdBy, Instant createdAt, String dispatchedBy, Instant dispatchedAt, String deliveredBy, Instant deliveredAt, String failedBy, Instant failedAt, String failureReason, String cancelledBy, Instant cancelledAt, String returnedToWarehouseBy, Instant returnedToWarehouseAt, UUID returnWarehouseId) {
        this(id, saleId, status, recipientName, recipientPhone, address, cityRegion, deliveryCost, carrierName, trackingNumber, comment, createdBy, createdAt, dispatchedBy, dispatchedAt, deliveredBy, deliveredAt, failedBy, failedAt, failureReason, cancelledBy, cancelledAt, returnedToWarehouseBy, returnedToWarehouseAt, returnWarehouseId, null);
    }


    public Delivery {
        if (id == null) {
            throw new IllegalArgumentException("Delivery id обязателен");
        }

        if (saleId == null) {
            throw new IllegalArgumentException("Продажа обязательна");
        }

        if (status == null) {
            throw new IllegalArgumentException("Статус доставки обязателен");
        }

        recipientName = optionalText(recipientName, 200);

        recipientPhone = requireText(
                recipientPhone,
                "Телефон получателя обязателен",
                50
        );

        address = requireText(
                address,
                "Адрес доставки обязателен",
                500
        );

        cityRegion = optionalText(cityRegion, 200);
        carrierName = optionalText(carrierName, 200);
        trackingNumber = optionalText(trackingNumber, 200);
        comment = optionalText(comment, 2000);
        failureReason = optionalText(failureReason, 1000);

        if (deliveryCost == null) {
            throw new IllegalArgumentException(
                    "Стоимость доставки обязательна"
            );
        }

        if (deliveryCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "Стоимость доставки не может быть отрицательной"
            );
        }

        if (deliveryCost.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Стоимость доставки указывается только в целых сомах"
            );
        }

        createdBy = requireText(
                createdBy,
                "Инициатор создания доставки обязателен",
                200
        );

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Время создания доставки обязательно"
            );
        }

        validateState(
                status,
                dispatchedBy,
                dispatchedAt,
                deliveredBy,
                deliveredAt,
                failedBy,
                failedAt,
                failureReason,
                cancelledBy,
                cancelledAt,
                returnedToWarehouseBy,
                returnedToWarehouseAt,
                returnWarehouseId
        );
    }

    public boolean ready() {
        return status == DeliveryStatus.READY;
    }

    public boolean inTransit() {
        return status == DeliveryStatus.IN_TRANSIT;
    }

    public boolean delivered() {
        return status == DeliveryStatus.DELIVERED;
    }

    public boolean failed() {
        return status == DeliveryStatus.FAILED;
    }

    public boolean cancelled() {
        return status == DeliveryStatus.CANCELLED;
    }

    public boolean returnedToWarehouse() {
        return returnedToWarehouseAt != null;
    }

    private static void validateState(
            DeliveryStatus status,
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
        switch (status) {
            case READY -> {
                requireNull(dispatchedBy, dispatchedAt);
                requireNull(deliveredBy, deliveredAt);
                requireNull(failedBy, failedAt);
                requireNull(cancelledBy, cancelledAt);
            }

            case IN_TRANSIT -> {
                requirePair(
                        dispatchedBy,
                        dispatchedAt,
                        "Некорректное состояние отправленной доставки"
                );

                requireNull(deliveredBy, deliveredAt);
                requireNull(failedBy, failedAt);
                requireNull(cancelledBy, cancelledAt);
            }

            case DELIVERED -> {
                requirePair(
                        dispatchedBy,
                        dispatchedAt,
                        "Некорректное состояние доставки"
                );

                requirePair(
                        deliveredBy,
                        deliveredAt,
                        "Некорректное состояние завершённой доставки"
                );

                requireNull(failedBy, failedAt);
                requireNull(cancelledBy, cancelledAt);
            }

            case FAILED -> {
                requirePair(
                        dispatchedBy,
                        dispatchedAt,
                        "Неуспешная доставка должна быть отправлена"
                );

                requirePair(
                        failedBy,
                        failedAt,
                        "Некорректное состояние неуспешной доставки"
                );

                if (failureReason == null) {
                    throw new IllegalArgumentException(
                            "Причина неуспешной доставки обязательна"
                    );
                }

                requireNull(deliveredBy, deliveredAt);
                requireNull(cancelledBy, cancelledAt);
            }

            case CANCELLED -> {
                requirePair(
                        cancelledBy,
                        cancelledAt,
                        "Некорректное состояние отменённой доставки"
                );

                requireNull(dispatchedBy, dispatchedAt);
                requireNull(deliveredBy, deliveredAt);
                requireNull(failedBy, failedAt);
            }
        }

        boolean noReturn =
                returnedToWarehouseBy == null
                        && returnedToWarehouseAt == null
                        && returnWarehouseId == null;

        boolean completeReturn =
                returnedToWarehouseBy != null
                        && !returnedToWarehouseBy.isBlank()
                        && returnedToWarehouseAt != null
                        && returnWarehouseId != null;

        if (!noReturn && !completeReturn) {
            throw new IllegalArgumentException(
                    "Некорректные данные возврата доставки на склад"
            );
        }

        if (completeReturn
                && status != DeliveryStatus.FAILED) {
            throw new IllegalArgumentException(
                    "На склад можно вернуть только товар из неуспешной доставки"
            );
        }
    }

    private static void requirePair(
            String actor,
            Instant time,
            String message
    ) {
        if (actor == null
                || actor.isBlank()
                || time == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNull(
            String actor,
            Instant time
    ) {
        if (actor != null || time != null) {
            throw new IllegalArgumentException(
                    "Некорректное состояние доставки"
            );
        }
    }

    private static String requireText(
            String value,
            String message,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Значение превышает допустимую длину"
            );
        }

        return normalized;
    }

    private static String optionalText(
            String value,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Значение превышает допустимую длину"
            );
        }

        return normalized;
    }
}