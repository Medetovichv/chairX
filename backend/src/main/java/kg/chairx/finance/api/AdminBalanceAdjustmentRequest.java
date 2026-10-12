package kg.chairx.finance.api;

import jakarta.validation.constraints.*;
import kg.chairx.finance.domain.FinanceAccount;
import java.math.BigDecimal;
import java.util.UUID;

/** Explicit adjustment with a stable operation key, not a mutable account opening balance. */
public record AdminBalanceAdjustmentRequest(
        @NotNull FinanceAccount account,
        @NotNull @DecimalMin("0") @Digits(integer=17, fraction=0) BigDecimal targetBalance,
        @NotNull UUID idempotencyKey,
        @NotBlank @Size(min=5, max=500) String reason
) {}
