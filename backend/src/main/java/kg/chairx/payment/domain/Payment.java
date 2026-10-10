package kg.chairx.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID saleId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        String comment,
        String paidBy,
        Instant paidAt,
        String cancelledBy,
        Instant cancelledAt,
        String cancellationReason,
        PaymentChannel channel
) {
    public Payment(
            UUID id, UUID saleId, BigDecimal amount, PaymentMethod method,
            PaymentStatus status, String reference, String comment,
            String paidBy, Instant paidAt, String cancelledBy,
            Instant cancelledAt, String cancellationReason) {
        this(id,saleId,amount,method,status,reference,comment,paidBy,paidAt,
                cancelledBy,cancelledAt,cancellationReason,
                PaymentChannel.defaultFor(method));
    }


    public Payment {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Payment id обязателен"
            );
        }

        if (saleId == null) {
            throw new IllegalArgumentException(
                    "Продажа обязательна"
            );
        }

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Сумма оплаты обязательна"
            );
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Сумма оплаты не может быть отрицательной"
            );
        }

        try {
            amount = amount.setScale(0);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Сумма оплаты должна быть указана в целых сомах"
            );
        }

        if (method == null) {
            throw new IllegalArgumentException(
                    "Способ оплаты обязателен"
            );
        }

        channel = channel == null ? PaymentChannel.defaultFor(method) : channel;
        if (!channel.compatibleWith(method)) {
            throw new IllegalArgumentException("Канал оплаты не соответствует финансовому счёту");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Статус оплаты обязателен"
            );
        }

        reference = normalizeOptional(
                reference,
                200,
                "Референс оплаты"
        );

        comment = normalizeOptional(
                comment,
                1000,
                "Комментарий"
        );

        paidBy = requireActor(
                paidBy,
                "Инициатор оплаты обязателен"
        );

        if (paidAt == null) {
            throw new IllegalArgumentException(
                    "Время оплаты обязательно"
            );
        }

        validateState(
                status,
                cancelledBy,
                cancelledAt,
                cancellationReason
        );
    }

    public boolean paid() {
        return status == PaymentStatus.PAID;
    }

    public boolean cancelled() {
        return status == PaymentStatus.CANCELLED;
    }

    public Payment cancel(
            String actor,
            Instant time,
            String reason
    ) {
        if (!paid()) {
            throw new IllegalStateException(
                    "Отменить можно только зарегистрированную оплату"
            );
        }

        String normalizedActor = requireActor(
                actor,
                "Инициатор отмены оплаты обязателен"
        );

        if (time == null) {
            throw new IllegalArgumentException(
                    "Время отмены оплаты обязательно"
            );
        }

        String normalizedReason = requireText(
                reason,
                1000,
                "Причина отмены оплаты обязательна"
        );

        return new Payment(
                id,
                saleId,
                amount,
                method,
                PaymentStatus.CANCELLED,
                reference,
                comment,
                paidBy,
                paidAt,
                normalizedActor,
                time,
                normalizedReason,
                channel
        );
    }

    private static void validateState(
            PaymentStatus status,
            String cancelledBy,
            Instant cancelledAt,
            String cancellationReason
    ) {
        switch (status) {
            case PAID -> {
                if (cancelledBy != null
                        || cancelledAt != null
                        || cancellationReason != null) {
                    throw new IllegalArgumentException(
                            "Некорректное состояние зарегистрированной оплаты"
                    );
                }
            }

            case CANCELLED -> {
                if (cancelledBy == null
                        || cancelledBy.isBlank()
                        || cancelledAt == null
                        || cancellationReason == null
                        || cancellationReason.isBlank()) {
                    throw new IllegalArgumentException(
                            "Некорректное состояние отменённой оплаты"
                    );
                }
            }
        }
    }

    private static String requireActor(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        String normalized = value.trim();

        if (normalized.length() > 200) {
            throw new IllegalArgumentException(
                    "Имя инициатора не должно превышать 200 символов"
            );
        }

        return normalized;
    }

    private static String requireText(
            String value,
            int maxLength,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Значение не должно превышать "
                            + maxLength
                            + " символов"
            );
        }

        return normalized;
    }

    private static String normalizeOptional(
            String value,
            int maxLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName
                            + " не должен превышать "
                            + maxLength
                            + " символов"
            );
        }

        return normalized;
    }
}