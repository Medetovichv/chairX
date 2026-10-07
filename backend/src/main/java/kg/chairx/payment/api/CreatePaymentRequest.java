package kg.chairx.payment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.payment.domain.PaymentMethod;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull
        UUID saleId,

        @NotNull
        PaymentMethod method,

        @Size(max = 200)
        String reference,

        @Size(max = 1000)
        String comment
) {
}