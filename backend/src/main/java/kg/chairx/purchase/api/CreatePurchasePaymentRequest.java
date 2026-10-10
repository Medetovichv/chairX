package kg.chairx.purchase.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import kg.chairx.purchase.domain.PurchasePaymentKind;
import kg.chairx.finance.domain.FinanceAccount;

public record CreatePurchasePaymentRequest(
        @NotNull UUID idempotencyKey,
        @NotNull PurchasePaymentKind paymentKind,
        @NotNull FinanceAccount account,
        @NotNull @DecimalMin("1") @Digits(integer=17, fraction=0) BigDecimal amount,
        @Size(max=200) String reference,
        @Size(max=1000) String comment
) {}
