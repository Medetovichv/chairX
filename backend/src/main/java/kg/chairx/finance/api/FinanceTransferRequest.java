package kg.chairx.finance.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import kg.chairx.finance.domain.FinanceAccount;
import java.math.BigDecimal;
import java.util.UUID;

public record FinanceTransferRequest(
        @NotNull UUID transferId,
        @NotNull FinanceAccount from,
        @NotNull FinanceAccount to,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 17, fraction = 0)
        BigDecimal amount
) {}
