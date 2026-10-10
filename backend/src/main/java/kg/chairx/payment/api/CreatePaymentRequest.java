package kg.chairx.payment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentChannel;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull
        UUID saleId,

        @NotNull
        PaymentMethod method,

        @Size(max = 200)
        String reference,

        @Size(max = 1000)
        String comment,
        PaymentChannel channel
) {
    public CreatePaymentRequest(UUID saleId, PaymentMethod method,
                                String reference, String comment) {
        this(saleId,method,reference,comment,null);
    }
}