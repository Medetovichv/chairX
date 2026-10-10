package kg.chairx.finance.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import kg.chairx.finance.domain.FinanceAccount;
import java.math.BigDecimal;

public record OpeningBalanceRequest(
        @NotNull FinanceAccount account,
        @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 0)
        BigDecimal amount
) {}
