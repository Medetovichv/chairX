package kg.chairx.refund.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Refund(
        UUID id,
        UUID saleId,
        UUID returnId,
        BigDecimal amount,
        RefundMethod method,
        String reason,
        String reference,
        String comment,
        String refundedBy,
        Instant refundedAt
) {

    public Refund {
        Objects.requireNonNull(
                id,
                "Refund id обязателен"
        );

        Objects.requireNonNull(
                saleId,
                "Sale id обязателен"
        );

        Objects.requireNonNull(
                amount,
                "Сумма возврата обязательна"
        );

        Objects.requireNonNull(
                method,
                "Способ возврата обязателен"
        );

        Objects.requireNonNull(
                reason,
                "Причина возврата обязательна"
        );

        Objects.requireNonNull(
                refundedBy,
                "Инициатор возврата обязателен"
        );

        Objects.requireNonNull(
                refundedAt,
                "Время возврата обязательно"
        );

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Сумма возврата должна быть положительной"
            );
        }

        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Сумма возврата должна быть указана в целых сомах"
            );
        }

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Причина возврата обязательна"
            );
        }
    }
}