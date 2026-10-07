package kg.chairx.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailDeliveryRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}