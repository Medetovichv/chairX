package kg.chairx.finance.api;

import java.math.BigDecimal;

public record FinanceAccountResponse(
        String code,
        BigDecimal balance,
        boolean initialized
) {
}