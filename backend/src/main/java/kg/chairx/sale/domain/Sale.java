package kg.chairx.sale.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Sale(
        UUID id,
        String saleNumber,
        UUID customerId,
        FulfillmentType fulfillmentType,
        SaleStatus status,
        List<SaleItem> items,
        String createdBy,
        Instant createdAt,
        String fulfilledBy,
        Instant fulfilledAt,
        String cancelledBy,
        Instant cancelledAt,
        String comment
) {

    public Sale(UUID id, String saleNumber, UUID customerId, FulfillmentType fulfillmentType, SaleStatus status, List<SaleItem> items, String createdBy, Instant createdAt, String fulfilledBy, Instant fulfilledAt, String cancelledBy, Instant cancelledAt) {
        this(id, saleNumber, customerId, fulfillmentType, status, items, createdBy, createdAt, fulfilledBy, fulfilledAt, cancelledBy, cancelledAt, null);
    }


    public Sale {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Sale id обязателен"
            );
        }

        if (saleNumber == null || saleNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Номер продажи обязателен"
            );
        }

        if (fulfillmentType == null && status != SaleStatus.DRAFT
                && status != SaleStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Способ получения обязателен"
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Статус продажи обязателен"
            );
        }

        if (items == null) {
            items = List.of();
        } else {
            items = List.copyOf(items);
        }

        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Инициатор продажи обязателен"
            );
        }

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Время создания продажи обязательно"
            );
        }

        validateState(
                status,
                fulfilledBy,
                fulfilledAt,
                cancelledBy,
                cancelledAt
        );
    }

    public BigDecimal total() {
        return items.stream()
                .map(SaleItem::total)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    public boolean confirmed() {
        return status == SaleStatus.CONFIRMED;
    }

    public boolean fulfilled() {
        return status == SaleStatus.FULFILLED;
    }

    public boolean cancelled() {
        return status == SaleStatus.CANCELLED;
    }

    public Sale fulfill(
            String actor,
            Instant time
    ) {
        if (!confirmed()) {
            throw new IllegalStateException(
                    "Выдать можно только подтверждённую продажу"
            );
        }

        String normalizedActor = requireActor(actor);

        if (time == null) {
            throw new IllegalArgumentException(
                    "Время выдачи обязательно"
            );
        }

        return new Sale(
                id,
                saleNumber,
                customerId,
                fulfillmentType,
                SaleStatus.FULFILLED,
                items,
                createdBy,
                createdAt,
                normalizedActor,
                time,
                null,
                null,
                comment
        );
    }

    public Sale cancel(
            String actor,
            Instant time
    ) {
        if (!confirmed() && status != SaleStatus.DRAFT) {
            throw new IllegalStateException(
                    "Отменить можно только черновик или подтверждённую продажу"
            );
        }

        String normalizedActor = requireActor(actor);

        if (time == null) {
            throw new IllegalArgumentException(
                    "Время отмены обязательно"
            );
        }

        return new Sale(
                id,
                saleNumber,
                customerId,
                fulfillmentType,
                SaleStatus.CANCELLED,
                items,
                createdBy,
                createdAt,
                null,
                null,
                normalizedActor,
                time,
                comment
        );
    }

    private static String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(
                    "Инициатор операции обязателен"
            );
        }

        String normalized = actor.trim();

        if (normalized.length() > 200) {
            throw new IllegalArgumentException(
                    "Имя инициатора не должно превышать 200 символов"
            );
        }

        return normalized;
    }

    private static void validateState(
            SaleStatus status,
            String fulfilledBy,
            Instant fulfilledAt,
            String cancelledBy,
            Instant cancelledAt
    ) {
        switch (status) {
            case DRAFT -> {
                if (fulfilledBy != null || fulfilledAt != null
                        || cancelledBy != null || cancelledAt != null) {
                    throw new IllegalArgumentException("Некорректное состояние черновика");
                }
            }
            case CONFIRMED -> {
                if (fulfilledBy != null
                        || fulfilledAt != null
                        || cancelledBy != null
                        || cancelledAt != null) {
                    throw new IllegalArgumentException(
                            "Некорректное состояние подтверждённой продажи"
                    );
                }
            }

            case FULFILLED -> {
                if (fulfilledBy == null
                        || fulfilledBy.isBlank()
                        || fulfilledAt == null
                        || cancelledBy != null
                        || cancelledAt != null) {
                    throw new IllegalArgumentException(
                            "Некорректное состояние выданной продажи"
                    );
                }
            }

            case CANCELLED -> {
                if (cancelledBy == null
                        || cancelledBy.isBlank()
                        || cancelledAt == null
                        || fulfilledBy != null
                        || fulfilledAt != null) {
                    throw new IllegalArgumentException(
                            "Некорректное состояние отменённой продажи"
                    );
                }
            }
        }
    }
}