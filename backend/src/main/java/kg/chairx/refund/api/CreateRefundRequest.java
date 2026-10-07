package kg.chairx.refund.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.refund.domain.RefundMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRefundRequest(

        @NotNull(message = "Укажите продажу")
        UUID saleId,

        UUID returnId,

        @NotNull(message = "Укажите сумму возврата")
        @DecimalMin(
                value = "1",
                message = "Сумма возврата должна быть положительной"
        )
        @Digits(
                integer = 17,
                fraction = 0,
                message = "Сумма возврата должна быть указана в целых сомах"
        )
        BigDecimal amount,

        @NotNull(message = "Укажите способ возврата")
        RefundMethod method,

        @NotBlank(message = "Укажите причину возврата")
        @Size(
                max = 500,
                message = "Причина возврата не должна превышать 500 символов"
        )
        String reason,

        @Size(
                max = 200,
                message = "Ссылка на операцию не должна превышать 200 символов"
        )
        String reference,

        @Size(
                max = 1000,
                message = "Комментарий не должен превышать 1000 символов"
        )
        String comment,

        @NotNull(message = "Укажите ключ операции")
        UUID idempotencyKey
) {
}