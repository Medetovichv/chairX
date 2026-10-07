package kg.chairx.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPaymentRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}