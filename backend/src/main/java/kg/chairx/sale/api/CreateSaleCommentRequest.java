package kg.chairx.sale.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Idempotency key belongs to one exactly-identified user action. */
public record CreateSaleCommentRequest(@NotNull UUID idempotencyKey,
        @NotBlank @Size(max=2000) String text) {}
