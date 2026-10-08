package kg.chairx.exchange.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateExchangeSettlementRequest(
        @NotNull UUID idempotencyKey,
        @NotBlank String direction,
        @NotBlank String method,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 200) String reference
) {
}